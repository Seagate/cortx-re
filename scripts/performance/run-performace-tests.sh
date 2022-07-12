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
PRIMARY_NODES_FILE=$PWD/primary_nodes
CLIENT_NODES_FILE=$PWD/client_nodes
HOST_FILE=$PWD/hosts
SSH_KEY_FILE=/root/.ssh/id_rsa
cat $PRIMARY_NODES_FILE $CLIENT_NODES_FILE > $PWD/hosts
ALL_NODES=$(awk -F[,] '{print $1}' $HOST_FILE | cut -d'=' -f2) || (echo -e "\n###### Could not fetch ALL_NODES value.Please check provided hosts file ######"; exit)
PRIMARY_NODE=$(head -1 "$PRIMARY_NODES_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2) || { echo -e "\n###### Could not fetch PRIMARY_NODE value. Please check provided hosts file ######"; exit; }
PRIMARY_CRED=$(head -1 "$PRIMARY_NODES_FILE" | awk -F[,] '{print $3}' | cut -d'=' -f2) || { echo -e "\n###### Could not fetch PRIMARY_CRED value. Please check provided hosts file ######" ;exit; }
CLIENT_NODE=$(head -1 "$CLIENT_NODES_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2) || { echo -e "\n###### Could not fetch CLIENT_NODE value. Please check provided hosts file ######";exit; }
CLIENT_CRED=$(head -1 "$CLIENT_NODES_FILE" | awk -F[,] '{print $3}' | cut -d'=' -f2) || { echo -e "\n###### Could not fetch CLIENT_CRED value. Please check provided hosts file ######" ;exit; }

if [ $(echo $PRIMARY_NODE | tr ' ' '\n' | wc -l) -gt 2 ]; then
echo -e "\n###### There are multiple entries in hosts.Please check provided hosts file ######"
exit
fi

validation
generate_rsa_key
nodes_setup

scp_all_nodes _run-performace-tests-functions.sh ../../solutions/kubernetes/*

add_primary_separator "Fetch Endpoint URL,Access Key and Secret Key from CORTX Cluster"
SETUP_INFO=$(ssh_primary_node "export SOLUTION_FILE=$SOLUTION_FILE && /var/tmp/_run-performace-tests-functions.sh --fetch-setup-info")

export ENDPOINT_URL=$(echo $SETUP_INFO | tr ' ' '\n' | grep ENDPOINT_URL | cut -d'=' -f2)
export ACCESS_KEY=$(echo $SETUP_INFO | tr ' ' '\n' | grep ACCESS_KEY | cut -d'=' -f2) &&
export SECRET_KEY=$(echo $SETUP_INFO | tr ' ' '\n' | grep SECRET_KEY | cut -d'=' -f2) &&
export BUILD_URL=$(echo $SETUP_INFO | tr ' ' '\n' | grep BUILD_URL | cut -d'=' -f2)


echo $ENDPOINT_URL

add_primary_separator "Configure awscli on client"
ssh -o 'StrictHostKeyChecking=no' "$CLIENT_NODE" "
export ENDPOINT_URL=$ENDPOINT_URL &&
export ACCESS_KEY=$ACCESS_KEY &&
export SECRET_KEY=$SECRET_KEY &&
/var/tmp/_run-performace-tests-functions.sh --setup-client"

export CLUSTER_TYPE=$(echo $SETUP_INFO | tr ' ' '\n' | grep CLUSTER_TYPE | cut -d'=' -f2)

echo -e "\n\n########################################################################"
echo -e "# CORTX_TOOLS_REPO           : $CORTX_TOOLS_REPO                            "
echo -e "# CORTX_TOOLS_BRANCH         : $CORTX_TOOLS_BRANCH                          "
echo -e "# PRIMARY_NODE               : $PRIMARY_NODE                                "
echo -e "# CLIENT_NODE                : $CLIENT_NODE                                 "
echo -e "# ENDPOINT_URL               : $ENDPOINT_URL                                "
echo -e "# BUILD_URL                  : $BUILD_URL                                   "
echo -e "# CLUSTER_TYPE               : $CLUSTER_TYPE                                "
echo -e "############################################################################"

add_primary_separator "Execute PerfPro Sanity Suite"
ssh -o 'StrictHostKeyChecking=no' "$CLIENT_NODE" "
export GITHUB_TOKEN=$GITHUB_TOKEN &&
export SCRIPT_LOCATION=$SCRIPT_LOCATION &&
export CORTX_TOOLS_BRANCH=$CORTX_TOOLS_BRANCH &&
export CORTX_TOOLS_REPO=$CORTX_TOOLS_REPO &&
export PRIMARY_NODE=$PRIMARY_NODE &&
export CLIENT_NODE=$CLIENT_NODE &&
export PRIMARY_CRED=$PRIMARY_CRED &&
export CLIENT_CRED=$CLIENT_CRED &&
export ENDPOINT_URL=$ENDPOINT_URL &&
export BUILD_URL=$BUILD_URL &&
export CLUSTER_TYPE=$CLUSTER_TYPE &&
export DB_SERVER=$DB_SERVER &&
export DB_PORT=$DB_PORT &&
export DB_USER=$DB_USER &&
export DB_PASSWD=$DB_PASSWD &&
export DB_NAME=$DB_NAME &&
export DB_DATABASE=$DB_DATABASE &&
/var/tmp/_run-performace-tests-functions.sh --execute-perf-sanity"
