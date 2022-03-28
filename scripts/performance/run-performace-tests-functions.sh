#!/bin/bash
#
# Copyright (c) 2022 Seagate Technology LLC and/or its Affiliates
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
set -eo pipefail
source /var/tmp/functions.sh

function usage() {
    cat << HEREDOC
Usage : $0 [--setup-client, --fetch-setup-info]
where,
    --setup-client - Setup S3 Client.
    --fetch-setup-info - Fetch setup information for PerfPro.
HEREDOC
}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

create_endpoint_url() {
echo SOLUTION_FILE:$SOLUTION_FILE
ACCESS_KEY=$(yq e '.solution.common.s3.default_iam_users.auth_admin' $SOLUTION_FILE)
SECRET_KEY=$(yq e '.solution.secrets.content.s3_auth_admin_secret' $SOLUTION_FILE)
HTTP_PORT=$(kubectl get svc cortx-io-svc-0 -o=jsonpath='{.spec.ports[?(@.port==80)].nodePort}')
IP_ADDRESS=$(ifconfig eth1 | grep inet -w | awk '{print $2}')
ENDPOINT_URL="http://$IP_ADDRESS:$HTTP_PORT"

echo ENDPOINT_URL $ENDPOINT_URL
echo ACCESS_KEY $ACCESS_KEY
echo SECRET_KEY $SECRET_KEY
}
function run_io_sanity() {
   add_primary_separator "\tStarting IO Sanity Testing"

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

function clone_segate_tools_repo() {
    if [ -z "$SCRIPT_LOCATION" ]; then echo "SCRIPT_LOCATION not provided.Exiting..."; exit 1; fi
    if [ -z "$GITHUB_TOKEN" ]; then echo "GITHUB_TOKEN not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_TOOLS_REPO" ]; then echo "CORTX_SCRIPTS_REPO not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_TOOLS_BRANCH" ]; then echo "CORTX_SCRIPTS_BRANCH not provided.Exiting..."; exit 1; fi

    rm -rf $SCRIPT_LOCATION
    yum install git -y
    git clone https://$GITHUB_TOKEN@github.com/$CORTX_TOOLS_REPO $SCRIPT_LOCATION
    pushd $SCRIPT_LOCATION
    git checkout $CORTX_TOOLS_BRANCH
    popd
}

function update_setup_confiuration() {
    #Update root password in config.yaml
    sed -i '/CLUSTER_PASS/s/seagate1/$PRIMARY_CREDS/g' $SCRIPT_LOCATION/performance/PerfPro/roles/benchmark/vars/config.yml
    sed -i '/srvnode-1/s/ansible_host=/ansible_host='$PRIMARY_NODE'/' $SCRIPT_LOCATION/performance/PerfPro/inventories/hosts
    sed -i '/clientnode-1/s/ansible_host=/ansible_host='$CLIENT_NODE'/' $SCRIPT_LOCATION/performance/PerfPro/inventories/hosts
}

function execute_perfpro() {
    yum install ansible -y
    pushd $SCRIPT_LOCATION/performance/PerfPro
    ansible-playbook perfpro.yml -i inventories/hosts --extra-vars '{ "EXECUTION_TYPE" : "sanity" ,"REPOSITORY":{"motr":"cortx-motr","rgw":"cortx-rgw"} , "COMMIT_ID": { "main" : "d1234c" , "dev" : "a5678b"},"PR_ID" : "cortx-rgw/1234" , "USER":"Shailesh Vaidya","GID" : "729494" }' -v
    popd
}

function fetch-setup-info() {
    create_endpoint_url
}

function setup-client() {
    setup_awscli
    run_io_sanity
    clone_segate_tools_repo
    update_setup_confiuration
    #execute_perfpro
}

case $ACTION in
    --setup-client)
        setup-client
    ;;
    --fetch-setup-info)
        fetch-setup-info
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;
esac
