#!/bin/bash -x
#
# Copyright (c) 2021 Seagate Technology LLC and/or its Affiliates
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# For any questions about this software or licensing,
# please email opensource@seagate.com or cortx-questions@seagate.com.
#

function add_primary_separator() {
    printf "\n################################################################################\n"
    printf "\t\t$*\n"
    printf "################################################################################\n"
}

function add_secondary_separator() {
    echo -e '\n==================== '"$*"' ====================\n'
}

function add_common_separator() {
    echo -e '\n--------------- '"$*"' ---------------\n'
}

function check_status() {
    return_code=$?
    error_message=$1
    if [ $return_code -ne 0 ]; then
            add_common_separator ERROR: $error_message
            exit 1
    fi
    add_common_separator SUCCESS
}

function validation() {
    if [ ! -f "$HOST_FILE" ]; then
        echo "$HOST_FILE is not present"
        exit 1
    fi

    if [ "$SOLUTION_CONFIG_TYPE" == "manual" ]; then
        if [ ! -f "$SOLUTION_CONFIG" ]; then
            echo "$SOLUTION_CONFIG is not present"
            exit 1
        fi
    fi
}

function generate_rsa_key() {
    if [ ! -f "$SSH_KEY_FILE" ]; then
        ssh-keygen -b 2048 -t rsa -f $SSH_KEY_FILE -q -N ""
     else
        echo $SSH_KEY_FILE already present
    fi
}

function passwordless_ssh() {
    local NODE=$1
    local USER=$2
    local PASS=$3
    ping -c1 -W1 -q $NODE
    check_status
    yum list pdsh -q || yum install epel-release -y
    yum install sshpass openssh-clients pdsh -y
    check_status "$NODE: Package installation failed"
    sshpass -p "$PASS" ssh-copy-id -f -o StrictHostKeyChecking=no -i ~/.ssh/id_rsa.pub "$USER"@"$NODE"
    check_status "$NODE: Passwordless ssh setup failed. Please validate provided credentails"
}

function nodes_setup() {
    for ssh_node in $(cat "$HOST_FILE")
    do
        local NODE=$(echo "$ssh_node" | awk -F[,] '{print $1}' | cut -d'=' -f2)
        local USER=$(echo "$ssh_node" | awk -F[,] '{print $2}' | cut -d'=' -f2)
        local PASS=$(echo "$ssh_node" | awk -F[,] '{print $3}' | cut -d'=' -f2)

        add_secondary_separator Setting up passwordless ssh for $NODE
        passwordless_ssh "$NODE" "$USER" "$PASS"
    done
}

function k8s_deployment_type() {
    if [ "$(wc -l < $HOST_FILE)" == "1" ]; then
        SINGLE_NODE_DEPLOYMENT="True"
        add_secondary_separator Single Node Deployment
    fi

    if [ "$(wc -l < $HOST_FILE)" -ne "1" ]; then
        SINGLE_NODE_DEPLOYMENT="False"
        local UNTAINT_PRIMARY=$1
        if [ "$UNTAINT_PRIMARY" == "false" ]; then
            local NODES="$(wc -l < $HOST_FILE)"
            local PRIMARY_NODE=1
            local NODES="$((NODES-PRIMARY_NODE))"
            add_secondary_separator $NODES node deployment
        else
            local NODES="$(wc -l < $HOST_FILE)"
            add_secondary_separator $NODES node deployment
        fi
    fi
}

function cortx_deployment_type() {
    if [ "$(wc -l < $HOST_FILE)" == "1" ]; then
        SINGLE_NODE_DEPLOYMENT="True"
        add_secondary_separator Single Node Deployment
    fi

    if [ "$(wc -l < $HOST_FILE)" -ne "1" ]; then
        SINGLE_NODE_DEPLOYMENT="False"
        local NODES=$(wc -l < $HOST_FILE)
        local TAINTED_NODES=$(ssh_primary_node bash << EOF
kubectl get nodes -o jsonpath="{range .items[*]}{.metadata.name} {.spec.taints[?(@.effect=='NoSchedule')].effect}{\"\n\"}{end}" | grep  NoSchedule | wc -l
EOF
)
        local NODES="$((NODES-TAINTED_NODES))"
        add_secondary_separator $NODES node deployment
    fi
}

function scp_all_nodes() {
    for node in $ALL_NODES
        do 
            scp -q $* "$node":/var/tmp/
        done
}

function scp_primary_node() {
    for primary_nodes in $PRIMARY_NODE
        do
            scp -q $* "$primary_nodes":/var/tmp/
        done
}

function scp_ceph_nodes() {
    for ceph_nodes in $CEPH_NODES
        do
            scp -q ${@:2} "$ceph_nodes":$1
        done
}

function ssh_all_nodes() {
    for nodes in $ALL_NODES
        do
            ssh -o 'StrictHostKeyChecking=no' "$nodes" $*
        done
}

function ssh_primary_node() {
    ssh -o 'StrictHostKeyChecking=no' "$PRIMARY_NODE" $*
}

function setup_awscli() {
   add_secondary_separator "Setup awscli"
   
   # Configure plugin, api and endpoints.
   add_common_separator "Setup aws s3 plugin endpoints"
   aws configure set plugins.endpoint awscli_plugin_endpoint
   check_status "Failed to set awscli s3 plugin endpoint"
   add_common_separator "Setup aws s3 endpoint url"
   aws configure set s3.endpoint_url $ENDPOINT_URL
   check_status "Failed to set awscli s3 endpoint url"
   add_common_separator "Setup default aws region"
   aws configure set default.region us-east-1
   check_status "Failed to set default aws region"
   add_common_separator "Setup awscli s3api endpoint url"
   aws configure set s3api.endpoint_url $ENDPOINT_URL
   check_status "Failed to set awscli s3 api endpoint url"

   # Setup awscli authentication.
   add_common_separator "Setup aws access key"
   aws configure set aws_access_key_id $ACCESS_KEY
   check_status "Failed to set awscli access key"
   add_common_separator "Setup aws secret key"
   aws configure set aws_secret_access_key $SECRET_KEY
   check_status "Failed to set awscli secret key"
   cat /root/.aws/config
   add_primary_separator "Successfully installed and configured awscli"
}

function install_awscli() {
   add_primary_separator "\tInstall and setup awscli"

   add_secondary_separator "Check and install pip3 if not present:"
   if ! which pip3; then
      yum install python3-pip
   fi

   add_secondary_separator "Installing awscli"
   pip3 install awscli
   pip3 install awscli-plugin-endpoint

   if ! which aws; then
      add_common_separator "AWS CLI installation failed"
      exit 1
   fi
}

# Install yq 4.13.3

function install_yq() {
    YQ_VERSION=v4.13.3
    YQ_BINARY=yq_linux_386
    pip3 show yq && pip3 uninstall yq -y
    wget https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/${YQ_BINARY}.tar.gz -O - | tar xz && mv ${YQ_BINARY} /usr/bin/yq
    if [ -f /usr/local/bin/yq ]; then rm -rf /usr/local/bin/yq; fi    
    ln -s /usr/bin/yq /usr/local/bin/yq
}

function run_io_sanity() {
   add_primary_separator "\tStarting IO Sanity Testing"

   add_primary_separator "\tClean up existing buckets"
   for bucket in $(aws s3 ls | awk '{ print $NF}'); do add_common_separator "Deleteing $bucket"; aws s3 rm s3://$bucket --recursive && aws s3 rb s3://$bucket; done

   BUCKET="test-bucket"
   FILE1="file10mb"
   FILE2="test-obj.bin"

   add_common_separator "Creating S3 bucket:- '$BUCKET'"
   aws s3 mb s3://$BUCKET
   check_status "Failed to create bucket"
   aws s3 ls
   check_status "Failed to list buckets"

   add_common_separator "Create files to upload to '$BUCKET' bucket"
   echo -e "\nCreating '$FILE1'"
   dd if=/dev/zero of=$FILE1 bs=1M count=10
   echo -e "\nCreating '$FILE2'"
   date > $FILE2

   add_common_separator "Uploading '$FILE1' file to '$BUCKET' bucket"
   aws s3 cp $FILE1 s3://$BUCKET/file10MB
   check_status "Failed to upload '$FILE1' to '$BUCKET'"
   add_common_separator "Uploading '$FILE2' file to '$BUCKET' bucket"
   aws s3 cp $FILE2 s3://$BUCKET
   check_status "Failed to upload '$FILE2' to '$BUCKET'"

   add_common_separator "List files in '$BUCKET' bucket"
   aws s3 ls s3://$BUCKET
   check_status "Failed to list files in '$BUCKET'"

   add_common_separator "Download '$FILE1' as 'file10mbDn' and check diff"
   aws s3 cp s3://$BUCKET/file10MB file10mbDn
   check_status "Failed to download '$FILE1' as 'file10mbDn' from '$BUCKET'"
   FILE_DIFF=$(diff $FILE1 file10mbDn)

   if [[ $FILE_DIFF ]]; then
      echo -e "\nDIFF Status: $FILE_DIFF"
   else
      echo -e "\nDIFF Status: The files $FILE1 and file10mbDn are similar."
   fi

   add_common_separator "Remove all files in '$BUCKET' bucket"
   aws s3 rm s3://$BUCKET --recursive
   check_status "Failed to delete all files from '$BUCKET'"

   add_common_separator "Remove '$BUCKET' bucket"
   aws s3 rb s3://$BUCKET
   check_status "Failed to delete '$BUCKET'"
   add_primary_separator "\tSuccessfully Passed IO Sanity Testing"
}