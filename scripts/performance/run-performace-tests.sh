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

source ../../solutions/kubernetes/functions.sh

function check_params() {
    if [ -z "$CORTX_TOOLS_REPO" ]; then echo "CORTX_TOOLS_REPO not provided.Using Default Seagate/seagate-tools"; CORTX_TOOLS_REPO="Seagate/seagate-tools" ; fi
    if [ -z "$CORTX_TOOLS_BRANCH" ]; then echo "CORTX_TOOLS_BRANCH not provided.Using Default main."; CORTX_TOOLS_BRANCH="main"; fi
    if [ -z "$SOLUTION_FILE" ]; then echo "SOLUTION_FILE not provided.Using Default location /root/deploy-scripts/k8_cortx_cloud/solution.yaml..."; SOLUTION_FILE=/root/deploy-scripts/k8_cortx_cloud/solution.yaml; fi
    if [ -z "$SCRIPT_LOCATION" ]; then echo "SCRIPT_LOCATION not provided.Using Default location /root/performance-scripts "; SCRIPT_LOCATION="/root/performance-scripts"; fi
    if [ -z "$GITHUB_TOKEN" ]; then echo "GITHUB_TOKEN not provided.Exiting..."; exit 1; fi
}

add_primary_separator "Fetching setup details"

check_params
HOST_FILE=$PWD/hosts
SSH_KEY_FILE=/root/.ssh/id_rsa
ALL_NODES=$(awk -F[,] '{print $1}' $HOST_FILE | cut -d'=' -f2)
PRIMARY_NODE=$(grep "role=server" $HOST_FILE | awk -F[,] '{print $1}' | cut -d'=' -f2)
PRIMARY_CRED=$(grep "role=server" $HOST_FILE | awk -F[,] '{print $3}' | cut -d'=' -f2)
CLIENT_NODE=$(grep "role=client" $HOST_FILE | awk -F[,] '{print $1}' | cut -d'=' -f2)

validation
generate_rsa_key
nodes_setup

scp_all_nodes run-performace-tests-functions.sh ../../solutions/kubernetes/*

add_primary_separator "Fetch Endpoint URL,Access Key and Secret Key from CORTX Cluster"
SETUP_INFO=$(ssh_primary_node "export SOLUTION_FILE=$SOLUTION_FILE && /var/tmp/run-performace-tests-functions.sh --fetch-setup-info")

add_primary_separator "Configure awscli on client"
ssh -o 'StrictHostKeyChecking=no' "$CLIENT_NODE" "
export ENDPOINT_URL=$(echo $SETUP_INFO | tr ' ' '\n' | grep ENDPOINT_URL | cut -d'=' -f2) &&
export ACCESS_KEY=$(echo $SETUP_INFO | tr ' ' '\n' | grep ACCESS_KEY | cut -d'=' -f2) &&
export SECRET_KEY=$(echo $SETUP_INFO | tr ' ' '\n' | grep SECRET_KEY | cut -d'=' -f2) &&
/var/tmp/run-performace-tests-functions.sh --setup-client"

add_primary_separator "Execute PerfPro Sanity"
ssh -o 'StrictHostKeyChecking=no' "$CLIENT_NODE" "
export GITHUB_TOKEN=$GITHUB_TOKEN &&
export SCRIPT_LOCATION=$SCRIPT_LOCATION &&
export CORTX_TOOLS_BRANCH=main &&
export CORTX_TOOLS_REPO="Seagate/seagate-tools" &&
export PRIMARY_NODE=$PRIMARY_NODE &&
export CLIENT_NODE=$CLIENT_NODE &&
export PRIMARY_CRED=$PRIMARY_CRED &&
export BUILD_URL=$(echo $SETUP_INFO | tr ' ' '\n' | grep BUILD_URL | cut -d'=' -f2) &&
/var/tmp/run-performace-tests-functions.sh --execute-perf-sanity"
