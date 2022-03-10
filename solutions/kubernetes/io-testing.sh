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

set -euo pipefail # exit on failures

source /var/tmp/functions.sh

add_separator Setup awscli.

echo -e "\nInstall awscli:-\n"
pip3 install awscli
pip3 install awscli-plugin-endpoint

if ! which aws; then
  add_separator "AWS CLI installation failed"
  exit 1
fi

echo -e "\nCreate K8s NodePort service for exposing rgw to external clients."
cat > /root/s3-rgw-service.yaml <<EOF
apiVersion: v1
kind: Service
metadata:
  name: s3-rgw-service
  labels:
    name: cortx-server
spec:
  type: NodePort
  ports:
  - port: 8000
    nodePort: 31000
  selector:
    name: cortx-server
EOF
kubectl apply -f s3-rgw-service.yaml

access_key=$(kubectl get pods | grep cortx-server- | cut -d " " -f1 | head -1 | xargs -I{} kubectl exec -t {} -c cortx-rgw -- cat /etc/cortx/cluster.conf | grep auth_admin | head -2 | tail -1 | cut -d ":" -f2 | sed -e 's/^[[:space:]]*//')
secret_key=$(cat /root/deploy-scripts/k8_cortx_cloud/solution.yaml | grep s3_auth_admin_secret: | cut -d ":" -f2)
endpoint_url="http://"$(ifconfig eth1 | sed -nre '/^[^ ]+/{N;s/^([^ ]+).*inet *([^ ]+).*/\2/p}')":31000" 
mkdir -p /root/.aws/

echo -e "\nSetup aws s3 endpoints:-\n"
aws configure set plugins.endpoint awscli_plugin_endpoint
check_status "Failed to set awscli s3 plugin endpoint."
echo -e "\nSetup aws access key:-\n"
aws configure set aws_access_key_id $access_key
check_status "Failed to set awscli access key."
echo -e "\nSetup aws secret key:-\n"
aws configure set aws_secret_access_key $secret_key
check_status "Failed to set awscli secret key."

echo -e "\nSetup aws s3 endpoints:-\n"
aws configure set s3.endpoint_url $endpoint_url
check_status "Failed to set awscli s3 plugin endpoint."
aws configure set s3api.endpoint_url $endpoint_url
check_status "Failed to set awscli s3 plugin endpoint."

cat /root/.aws/config

add_separator Successfully configured awscli.

add_separator Starting IO testing.

BUCKET="test-bucket"
FILE1="file10mb"
FILE2="test-obj.bin"

echo -e "\nCreating S3 bucket:- '$BUCKET'\n"
aws s3 mb s3://$BUCKET
check_status "Failed to create bucket."
aws s3 ls
check_status "Failed to list buckets."

echo -e "\nCreating files to upload to '$BUCKET' bucket:-"
echo -e "\nCreating '$FILE1'"
dd if=/dev/zero of=$FILE1 bs=1M count=10
echo -e "\nCreating '$FILE2'"
date > $FILE2

echo -e "\nUploading '$FILE1' file to '$BUCKET' bucket:-\n"
aws s3 cp $FILE1 s3://$BUCKET/file10MB
check_status "Failed to upload '$FILE1' to '$BUCKET'."
echo -e "\nUploading '$FILE2' file to '$BUCKET' bucket:-\n"
aws s3 cp $FILE2 s3://$BUCKET
check_status "Failed to upload '$FILE2' to '$BUCKET'."

echo -e "\nList files in '$BUCKET' bucket:-\n"
aws s3 ls s3://$BUCKET
check_status "Failed to list files in '$BUCKET'."

echo -e "\nDownload '$FILE1' as 'file10mbDn' and check diff:-\n"
aws s3 cp s3://$BUCKET/file10MB file10mbDn
check_status "Failed to download '$FILE1' as 'file10mbDn' from '$BUCKET'."
FILE_DIFF=$(diff $FILE1 file10mbDn)

if [[ $FILE_DIFF ]]; then
   echo -e "\nDIFF Status: $FILE_DIFF"
else
   echo -e "\nDIFF Status: The files $FILE1 and file10mbDn are similar."
fi

echo -e "\nRemove all files in '$BUCKET' bucket:-\n"
aws s3 rm s3://$BUCKET --recursive
check_status "Failed to delete all files from '$BUCKET'."

echo -e "\nRemove '$BUCKET' bucket:-\n"
aws s3 rb s3://$BUCKET
check_status "Failed to delete '$BUCKET'."

echo -e "\nCleanup awscli files."
rm -rf ~/.aws/credentials
rm -rf ~/.aws/config

add_separator Successfully passed IO testing.
