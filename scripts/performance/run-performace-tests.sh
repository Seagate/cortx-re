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

source /root/cortx-re/solutions/kubernetes/functions.sh

function copy_scripts() {

HOST_FILE=$PWD/hosts
SSH_KEY_FILE=/root/.ssh/id_rsa
PRIMARY_NODE=$(grep "role=server" $HOST_FILE | awk -F[,] '{print $1}' | cut -d'=' -f2)
CLIENT_NODE=$(grep "role=client" $HOST_FILE | awk -F[,] '{print $1}' | cut -d'=' -f2)

validation
generate_rsa_key
nodes_setup

scp_primary_node run-performace-tests-functions.sh /root/cortx-re/solutions/kubernetes/*
}

copy_scripts

#Configured s3 client
#../../solutions/kubernetes/io-testing.sh
ENDPOINT_URL=$(ssh_primary_node /var/tmp/run-performace-tests-functions.sh --fetch-setup-info | grep ENDPOINT_URL | cut -d' ' -f2)
ACCESS_KEY=$(ssh_primary_node /var/tmp/run-performace-tests-functions.sh --fetch-setup-info | grep ACCESS_KEY | cut -d' ' -f2)
SECRET_KEY=$(ssh_primary_node /var/tmp/run-performace-tests-functions.sh --fetch-setup-info | grep SECRET_KEY | cut -d' ' -f2)
echo ENDPOINT_URL:$ENDPOINT_URL
echo ACCESS_KEY:$ACCESS_KEY
echo SECRET_KEY:$SECRET_KEY

ssh -o 'StrictHostKeyChecking=no' "$CLIENT_NODE" "
export ENDPOINT_URL=$ENDPOINT_URL && 
export ACCESS_KEY=$ACCESS_KEY && 
export SECRET_KEY=$SECRET_KEY && 
export GITHUB_TOKEN=$GITHUB_TOKEN &&
export CORTX_SCRIPTS_BRANCH=main &&
export CORTX_SCRIPTS_REPO="Seagate/seagate-tools" &&
export PRIMARY_NODE=$PRIMARY_NODE &&
export CLIENT_NODE=$CLIENT_NODE &&
/var/tmp/run-performace-tests-functions.sh --setup-client"
#Clone https://github.com/Seagate/seagate-tools repository

#Updated user credemtails in /root/seagate-tools/performance/PerfPro/roles/benchmark/vars/config.yml 

#Execute anisble playbook
