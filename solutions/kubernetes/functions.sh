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

source /etc/os-release

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

    if [[ "$ID" == "rocky" || "$ID" == "centos" ]]; then
        yum list pdsh -q || yum install epel-release -y
        yum install sshpass openssh-clients pdsh -y
        check_status "$NODE: Package installation failed"
    fi
    
    if [[ "$ID" == "ubuntu" ]]; then
        apt install -y pdsh sshpass openssh-client
        check_status "$NODE: Package installation failed"
    fi

    test -f /root/.ssh/known_hosts && sed -i '/"$NODE"/d' /root/.ssh/known_hosts
    
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

function pull_image() {
    local image="$1"
    if [[ "$image" =~ "latest" ]]; then
        docker pull "$image" &>/dev/null
        actual_image_tag=$(docker run --rm -t "$image" cat /opt/seagate/cortx/RELEASE.INFO | grep VERSION | awk -F'"' '{print $2}')        
        actual_image=$(echo "${image//2.0.0-latest/${actual_image_tag}}")
        docker rmi -f "$image" &>/dev/null
        docker pull "$actual_image" &>/dev/null
        echo "$actual_image"
    else
        docker pull "$image" &>/dev/null
        echo "$image"
    fi    
}

function update_image() {
    local POD_TYPE=$1
    local IMAGE=$2
    local SCRIPT_LOCATION="${3:-"/root/deploy-scripts/k8_cortx_cloud/"}"
    pushd $SCRIPT_LOCATION
        if [ $POD_TYPE == 'control-pod' ]; then 
            image=$IMAGE yq e -i '.solution.images.cortxcontrol = env(image)' solution.yaml
        elif [ $POD_TYPE == 'data-pod' ]; then
            image=$IMAGE yq e -i '.solution.images.cortxdata = env(image)' solution.yaml
        elif [ $POD_TYPE == 'server-pod' ]; then
            image=$IMAGE yq e -i '.solution.images.cortxserver = env(image)' solution.yaml
        elif [ $POD_TYPE == 'ha-pod' ]; then
            image=$IMAGE yq e -i '.solution.images.cortxha = env(image)' solution.yaml
        elif [ $POD_TYPE == 'client-pod' ]; then
            image=$IMAGE yq e -i '.solution.images.cortxclient = env(image)' solution.yaml
        else
            echo "Wrong POD_TYPE $POD_TYPE is provided. Please provide any one of the following options. [ control-pod, data-pod, server-pod, ha-pod, client-pod ]" && exit 1;
        fi         
    popd 
}

function copy_solution_config() {
    local SOLUTION_CONFIG=$1
    local SCRIPT_LOCATION=$2
	if [ -z "$SOLUTION_CONFIG" ]; then echo "SOLUTION_CONFIG not provided.Exiting..."; exit 1; fi
	echo "Copying $SOLUTION_CONFIG file" 
	pushd $SCRIPT_LOCATION
        if [ ! -f "$SOLUTION_CONFIG" ]; then echo "file $SOLUTION_CONFIG not available..."; exit 1; fi	
        cp $SOLUTION_CONFIG .
    popd 
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