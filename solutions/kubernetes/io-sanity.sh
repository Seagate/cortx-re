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
      if [[ "$ID" == "rocky" || "$ID" == "centos" ]]; then
         yum install -y python3-pip
      fi

      if [[ "$ID" == "ubuntu" ]]; then
         apt install -y python3-pip
      fi
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

      if ! which jq; then
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
      fi

      if [[ $CEPH_DOCKER_DEPLOYMENT = "true" ]]; then
         # Get credentials.
         access_key=$(cephadm shell -- radosgw-admin user info --uid=io-test | jq .keys[].access_key | tr -d '"')
         secret_key=$(cephadm shell -- radosgw-admin user info --uid=io-test | jq .keys[].secret_key | tr -d '"')      

      else
         # Get credentials.
         access_key=$(radosgw-admin user info --uid=io-test | jq .keys[].access_key | tr -d '"')
         secret_key=$(radosgw-admin user info --uid=io-test | jq .keys[].secret_key | tr -d '"')
      fi

      # Set endpoint url.
      endpoint_url="http://""$(hostname -i)"":9999"

   else
      # Get credentials.
      access_key=$(yq e '.solution.common.s3.default_iam_users.auth_admin' $SOLUTION_FILE)
      secret_key=$(kubectl get secrets/cortx-secret  --template={{.data.s3_auth_admin_secret}} | base64 -d)

      # Set endpoint url.
      endpoint_url="http://""$(kubectl get svc | grep cortx-io | awk '{ print $3 }')"":80"
   fi

   add_common_separator "AWS keys-:"
   echo "Access Key: $access_key"
   echo "Secret Key: $secret_key"

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
   BUCKET2="test-bucket2"
   FILE1="file10mb"
   FILE2="test-obj.bin"
   FILE3="file15mb"
   FILE4="file18mb"
   FILE5="Parts.json"

   add_common_separator "Creating S3 bucket:- '$BUCKET'"
   aws s3 mb s3://$BUCKET
   check_status "Failed to create bucket"
   add_common_separator "Creating S3 bucket:- '$BUCKET2'"
   aws s3api create-bucket --bucket $BUCKET2
   check_status "Failed to create bucket"
   aws s3 ls
   check_status "Failed to list buckets"

   add_common_separator "Head bucket operation for '$BUCKET2' bucket"
   aws s3api head-bucket --bucket $BUCKET2
   check_status "Failed in head-bucket operation for '$BUCKET2'"

   add_common_separator "List bucket operation"
   aws s3api list-buckets --query "Buckets[].Name"
   check_status "Failed to list buckets"

   add_common_separator "Create files to upload to '$BUCKET' bucket"
   echo -e "\nCreating '$FILE1'"
   dd if=/dev/zero of=$FILE1 bs=1M count=10
   echo -e "\nCreating '$FILE2'"
   date > $FILE2
   add_common_separator "Create files to upload to '$BUCKET2' bucket"
   echo -e "\nCreating '$FILE3'"
   dd if=/dev/zero of=$FILE3 bs=1M count=15
   echo -e "\nCreating '$FILE4'"
   dd if=/dev/zero of=$FILE4 bs=1M count=18
   echo -e "\nCreating '$FILE5"
   touch $FILE5

   add_common_separator "Uploading '$FILE1' file to '$BUCKET' bucket"
   aws s3 cp $FILE1 s3://$BUCKET/file10MB
   check_status "Failed to upload '$FILE1' to '$BUCKET'"
   add_common_separator "Uploading '$FILE2' file to '$BUCKET' bucket"
   aws s3 cp $FILE2 s3://$BUCKET
   check_status "Failed to upload '$FILE2' to '$BUCKET'"
   add_common_separator "Uploading '$FILE3' file to '$BUCKET2' bucket"
   aws s3api put-object --bucket $BUCKET2 --key $FILE3 --body $FILE3
   check_status "Failed to upload '$FILE3' to '$BUCKET2'"
   add_common_separator "Uploading '$FILE4' file to '$BUCKET2' bucket"
   aws s3api put-object --bucket $BUCKET2 --key $FILE4 --body $FILE4
   check_status "Failed to upload '$FILE4' to '$BUCKET2'"

   add_common_separator "Overwrite simple object '$FILE4' in '$BUCKET2' bucket"
   aws s3api put-object --bucket $BUCKET2 --key $FILE4 --body $FILE4
   check_status "Simple object overwrite operation failed"

   add_common_separator "Head object operation for '$FILE3' object"
   aws s3api head-object --bucket $BUCKET2 --key $FILE3
   check_status "Failed in head-object operation for object '$FILE3' in bucket '$BUCKET2'"

   add_common_separator "List files in '$BUCKET' bucket"
   aws s3 ls s3://$BUCKET
   check_status "Failed to list files in '$BUCKET'"
   add_common_separator "List files in '$BUCKET2' bucket"
   aws s3api list-objects --bucket $BUCKET2
   check_status "Failed to list files in '$BUCKET2'"

   add_common_separator "Download '$FILE1' as 'file10mbDn' and check diff"
   aws s3 cp s3://$BUCKET/file10MB file10mbDn
   check_status "Failed to download '$FILE1' as 'file10mbDn' from '$BUCKET'"
   FILE_DIFF=$(diff $FILE1 file10mbDn)
   if [[ $FILE_DIFF ]]; then
      echo -e "\nDIFF Status: $FILE_DIFF"
   else
      echo -e "\nDIFF Status: The files $FILE1 and file10mbDn are similar."
   fi

   add_common_separator "Download '$FILE3' as 'file15mbDn' and check diff"
   aws s3api get-object --bucket $BUCKET2 --key $FILE3 file15mbDn
   check_status "Failed to download '$FILE3' as 'file15mbDn' from '$BUCKET2'"
   FILE_DIFF2=$(diff $FILE3 file15mbDn)
   if [[ $FILE_DIFF2 ]]; then
      echo -e "\nDIFF Status: $FILE_DIFF2"
   else
      echo -e "\nDIFF Status: The files $FILE3 and file15mbDn are similar."
   fi

   add_common_separator "Copy object 'file10MB' from '$BUCKET' bucket to '$BUCKET2' bucket"
   aws s3api copy-object --copy-source $BUCKET/file10MB --key file10MB --bucket $BUCKET2
   check_status "Failed to copy object '$FILE1' from '$BUCKET' bucket to '$BUCKET2' bucket"

   add_common_separator "Remove all files in '$BUCKET' bucket"
   aws s3 rm s3://$BUCKET --recursive
   check_status "Failed to delete all files from '$BUCKET'"
   
   add_common_separator "Delete single object 'file10MB' from '$BUCKET2' bucket"
   aws s3api delete-object --bucket $BUCKET2 --key file10MB
   check_status "Failed to delete object 'file10MB' from '$BUCKET2'"

   add_common_separator "Delete multiple objects from '$BUCKET2' bucket"
   aws s3api delete-objects --bucket $BUCKET2 --delete Objects=[{Key=$FILE3},{Key=$FILE4}]
   check_status "Failed to delete multiple objects from '$BUCKET2'"
   
   add_common_separator "Multipart upload opearation on '$BUCKET2' bucket"
   rm -rf /tmp/upload.log
   aws s3api create-multipart-upload --bucket $BUCKET2 --key multipart >> /tmp/upload.log
   UPLOAD_ID=$(cat /tmp/upload.log | grep -o "UploadId[\"]:.*" | cut -c12-48)
   modified="${UPLOAD_ID:1:-1}"
   echo $modified
   aws s3api upload-part --bucket $BUCKET2 --key multipart --part-number 1 --body $FILE3 --upload-id $modified  > /tmp/etag1
   Etag1=$(cat /tmp/etag1 | grep -o "ETag[\"]:.*" | cut -c8-48)
   echo $Etag1
   aws s3api upload-part --bucket $BUCKET2 --key multipart --part-number 2 --body $FILE4 --upload-id $modified  > /tmp/etag2
   Etag2=$(cat /tmp/etag2 | grep -o "ETag[\"]:.*" | cut -c8-48)
   echo $Etag2
   echo '
   {
   "Parts": [
   {
   "ETag": '$Etag1',
   "PartNumber": 1
   },
   {
   "ETag": '$Etag2',
   "PartNumber": 2
   }
   ]
   }' > $FILE5
   check_status "Multipart upload failed to '$BUCKET2'"

   add_common_separator "List multipart upload '$BUCKET2' bucket"
   aws s3api list-multipart-uploads --bucket $BUCKET2
   check_status "List Multipart upload failed to '$BUCKET2'"

   add_common_separator "List parts '$BUCKET2' bucket"
   aws s3api list-parts --bucket $BUCKET2 --key multipart --upload-id $modified
   check_status "List parts failed to '$BUCKET2'"

   add_common_separator "Complete Multipart upload '$BUCKET2' bucket"
   aws s3api complete-multipart-upload --multipart-upload file://$FILE5 --bucket $BUCKET2 --key multipart --upload-id $modified 
   check_status "Complete Mutipart upload Failed on '$BUCKET2'Bucket"

   add_common_separator "Remove all files in '$BUCKET2' bucket"
   aws s3 rm s3://$BUCKET2 --recursive
   check_status "Failed to delete all files from '$BUCKET2'"

   add_common_separator "Remove '$BUCKET' bucket"
   aws s3 rb s3://$BUCKET
   check_status "Failed to delete '$BUCKET'"

   add_common_separator "Remove '$BUCKET2' bucket"
   aws s3api delete-bucket --bucket $BUCKET2
   check_status "Failed to delete '$BUCKET2'"

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