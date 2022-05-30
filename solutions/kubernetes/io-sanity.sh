#!/bin/bash
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

set -euo pipefail

source /var/tmp/functions.sh
source /etc/os-release

SOLUTION_FILE="/root/deploy-scripts/k8_cortx_cloud/solution.yaml"

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

function setup_awscli() {
   add_secondary_separator "Setup awscli"

   if [[ $CEPH_DEPLOYMENT = "true" ]]; then
      case "$ID" in
         rocky)
            yum install http://mirror.centos.org/centos/8-stream/AppStream/x86_64/os/Packages/jq-1.6-3.el8.x86_64.rpm -y 
         ;;
         centos)
            yum install jq -y
         ;;
         ubuntu)
            apt install -y jq
        ;;
      esac

      # Get credentials.
      access_key=$(radosgw-admin user info --uid=io-test | jq .keys[].access_key | tr -d '"')
      secret_key=$(radosgw-admin user info --uid=io-test | jq .keys[].secret_key | tr -d '"')
      endpoint_url="http://""$(hostname -i)"":9999"

   else
      # Get credentials.
      access_key=$(yq e '.solution.common.s3.default_iam_users.auth_admin' $SOLUTION_FILE)
      secret_key=$(yq e '.solution.secrets.content.s3_auth_admin_secret' $SOLUTION_FILE)
      endpoint_url="http://""$(kubectl get svc | grep cortx-io | awk '{ print $3 }')"":80"
   fi

   mkdir -p /root/.aws/

   # Configure plugin, api and endpoints.
   add_common_separator "Setup aws s3 plugin endpoints"
   aws configure set plugins.endpoint awscli_plugin_endpoint
   check_status "Failed to set awscli s3 plugin endpoint"
   add_common_separator "Setup aws s3 endpoint url"
   aws configure set s3.endpoint_url $endpoint_url
   check_status "Failed to set awscli s3 endpoint url"
   add_common_separator "Setup default aws region"
   aws configure set default.region us-east-1
   check_status "Failed to set default aws region"
   add_common_separator "Setup awscli s3api endpoint url"
   aws configure set s3api.endpoint_url $endpoint_url
   check_status "Failed to set awscli s3 api endpoint url"

   # Setup awscli authentication.
   add_common_separator "Setup aws access key"
   aws configure set aws_access_key_id $access_key
   check_status "Failed to set awscli access key"
   add_common_separator "Setup aws secret key"
   aws configure set aws_secret_access_key $secret_key
   check_status "Failed to set awscli secret key"
   cat /root/.aws/config
   add_primary_separator "Successfully installed and configured awscli"
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

   add_common_separator "Cleanup awscli files"
   rm -rf ~/.aws/credentials
   rm -rf ~/.aws/config

   add_primary_separator "\tSuccessfully Passed IO Sanity Testing"
}

function run_data_io_sanity() {
   kubectl exec -it $(kubectl get pods | awk '/cortx-client/{print $1; exit}') -c cortx-hax -- bash -c "pip3 install pandas xlrd \
   && yum install -y diffutils \
   && pushd /opt/seagate/cortx/motr/workload/ \
   && ./create_workload_from_excel -t /opt/seagate/cortx/motr/workload/sample_workload_excel_test.xls \
   && ./m0workload -t /opt/seagate/cortx/motr/workload/out*/workload_output.yaml \
   && if [ ! -z $(cat /tmp/sandbox/temp-*/report.txt | grep 'Return Value' | awk -F'=' '{if($2>0)print $2}' | wc -l) ]; then echo 'ERROR : IO Operation Failed' && exit 1; else echo 'SUCCESS : IO Operation Successful'; fi \
   && popd"   
}

# Execution
if [[ "$DEPLOYMENT_METHOD" == "data-only" ]]; then
   run_data_io_sanity
else   
   install_awscli
   setup_awscli
   run_io_sanity
fi   