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

add_separator Configuring S3 clients and endpoints.

echo -e "\nAdding cortx-s3iamcli repo:-\n"
yum-config-manager --add-repo http://cortx-storage.colo.seagate.com/releases/cortx/uploads/centos/centos-7.9.2009/s3server_uploads/
yum-config-manager --add-repo http://cortx-storage.colo.seagate.com/releases/cortx/github/kubernetes/centos-7.9.2009/last_successful/

echo -e "\nInstalling cortx-s3iamcli and awscli package:-\n"
yum install -y cortx-s3iamcli --nogpgcheck
pip3 install awscli
pip3 install awscli-plugin-endpoint

if ! which aws; then
  add_separator "AWS CLI installation failed"
  exit 1
fi

echo -e "\nAdd CORTX data pod IP to /etc/hosts and create s3iamcli auth directory and log file."
if [ "$DEPLOYMENT_TYPE" == "provisioner" ]; then
    echo "Deployment type is: $DEPLOYMENT_TYPE"
    CORTX_IO_SVC=$(kubectl get pods -o wide | grep server-node | awk '{print $6}' | head -1)
else
  CORTX_IO_SVC=$(kubectl get pods -o wide | grep cortx-server | awk '{print $6}' | head -1)
fi
echo "$CORTX_IO_SVC s3.seagate.com iam.seagate.com" >> /etc/hosts

mkdir -p /var/log/cortx/auth
touch /var/log/cortx/auth/s3iamcli.log

s3iamcli ListAccounts --ldapuser sgiamadmin --ldappasswd ldapadmin --no-ssl
check_status "Failed to list s3 accounts."

mkdir -p /root/.aws/

echo -e "\nCreate s3 account credentials:-\n"
s3iamcli CreateAccount -n admin -e admin@seagate.com \
    --ldapuser sgiamadmin --ldappasswd ldapadmin --no-ssl \
  > s3-account.txt
check_status "Failed to create s3 account."

cat s3-account.txt

set_VAL_for_key() {
  key="$1"
  VAL="$(cat s3-account.txt | sed 's/ *, */\n/g' | grep "$key" | sed "s,^$key *= *,,")"
}

set_VAL_for_key AccessKeyId
access_key="$VAL"
set_VAL_for_key SecretKey
secret_key="$VAL"

cat <<EOF > ~/.aws/credentials
[default]
aws_access_key_id = $access_key
aws_secret_access_key = $secret_key
EOF

echo -e "\nSetup aws s3 endpoints:-\n"
aws configure set plugins.endpoint awscli_plugin_endpoint
check_status "Failed to set awscli s3 plugin endpoint."
aws configure set s3.endpoint_url http://s3.seagate.com
check_status "Failed to set awscli s3 plugin endpoint."
aws configure set s3api.endpoint_url http://s3.seagate.com
check_status "Failed to set awscli s3 plugin endpoint."

cat /root/.aws/config

add_separator Successfully configured S3 clients and endpoints.
