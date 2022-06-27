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

source functions.sh cortx-deploy-functions.sh

HOST_FILE="$PWD/hosts"
PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
WORKER_NODES=$(grep -v "$PRIMARY_NODE" < "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2) || true
ALL_NODES=${WORKER_NODES}
REMOTE_SOLUTION_CONFIG="/var/tmp/solution.yaml"
SCRIPT_LOCATION="/root/deploy-scripts/k8_cortx_cloud"
SSH_KEY_FILE=/root/.ssh/id_rsa


function usage() {
    cat << HEREDOC
Usage : $0 [--upgrade,  --suspend, --resume, --cluster-status, --io-sanity]
where,
    --upgrade - Perform upgrade on given CORTX cluster. Options - [rolling-upgrade, cold-upgrade] 
    --suspend - Suspend current ongoing upgrade and save the state of upgrade.
    --resume - Resume suspended upgrade and continue from the last pod which was being upgraded.
    --upgrade-status - Display current state of upgrade.
    --cluster-status - Display current CORTX cluster status.
    --io-sanity - Execute basic IO operations on CORTX cluster.
HEREDOC
}

function check_params() {
    if [ -z "$CORTX_SCRIPTS_REPO" ]; then echo "CORTX_SCRIPTS_REPO is not provided, Using default: Seagate/cortx-k8s "; CORTX_SCRIPTS_REPO="Seagate/cortx-k8s"; fi
    if [ -z "$CORTX_SCRIPTS_BRANCH" ]; then echo "CORTX_SCRIPTS_BRANCH is not provided, Using default: cortx-test"; CORTX_SCRIPTS_BRANCH="cortx-test"; fi
    if [ -z "$CORTX_SERVER_IMAGE" ]; then echo "CORTX_SERVER_IMAGE is not provided, Using default : ghcr.io/seagate/cortx-rgw:2.0.0-latest"; CORTX_SERVER_IMAGE=ghcr.io/seagate/cortx-rgw:2.0.0-latest; fi
    if [ -z "$CORTX_DATA_IMAGE" ]; then echo "CORTX_DATA_IMAGE is not provided, Using default : ghcr.io/seagate/cortx-data:2.0.0-latest"; CORTX_DATA_IMAGE=ghcr.io/seagate/cortx-data:2.0.0-latest; fi
    if [ -z "$CORTX_CONTROL_IMAGE" ]; then echo "CORTX_CONTROL_IMAGE is not provided, Using default : ghcr.io/seagate/cortx-control:2.0.0-latest"; CORTX_CONTROL_IMAGE=ghcr.io/seagate/cortx-control:2.0.0-latest; fi
    if [ -z "$SOLUTION_CONFIG_TYPE" ]; then echo "SOLUTION_CONFIG_TYPE is not provided, Using default : manual"; SOLUTION_CONFIG_TYPE="manual"; fi
    if [ -z "$POD_TYPE" ]; then echo "POD_TYPE is not provided, Using default : all"; POD_TYPE="all"; fi
    if [ -z "$DEPLOYMENT_METHOD" ]; then echo "DEPLOYMENT_METHOD is not provided, Using default : standard"; DEPLOYMENT_METHOD="standard"; fi
    if [ -z "$UPGRADE_TYPE" ]; then echo "UPGRADE_TYPE is not provided, Using default : rolling-upgrade"; UPGRADE_TYPE="rolling-upgrade"; fi

    echo -e "\n\n########################################################################"
    echo -e "# CORTX_SCRIPTS_REPO           : $CORTX_SCRIPTS_REPO                   "
    echo -e "# CORTX_SCRIPTS_BRANCH         : $CORTX_SCRIPTS_BRANCH                 "
    echo -e "# CORTX_SERVER_IMAGE           : $CORTX_SERVER_IMAGE                   "
    echo -e "# CORTX_DATA_IMAGE             : $CORTX_DATA_IMAGE                     "
    echo -e "# CORTX_CONTROL_IMAGE          : $CORTX_CONTROL_IMAGE                  "
    echo -e "# SOLUTION_CONFIG_TYPE         : $SOLUTION_CONFIG_TYPE                 "
    echo -e "# POD_TYPE                     : $POD_TYPE                             "
    echo -e "# DEPLOYMENT_METHOD            : $DEPLOYMENT_METHOD                    "
    echo -e "# UPGRADE_TYPE                 : $UPGRADE_TYPE                         "
    echo -e "#########################################################################\n"
}

function check_cluster_status() {
    rm -rf /var/tmp/cortx-cluster-status.txt
    ssh_primary_node "export DEPLOYMENT_METHOD=$DEPLOYMENT_METHOD && /var/tmp/cortx-deploy-functions.sh --status" | tee /var/tmp/cortx-cluster-status.txt
}

function check_io_operations() {
    add_primary_separator "\tSetting up IO Sanity Testing"
    ssh_primary_node "export CEPH_DEPLOYMENT='false' && export DEPLOYMENT_METHOD=$DEPLOYMENT_METHOD && /var/tmp/cortx-deploy-functions.sh --io-sanity"
}

function setup_worker_nodes() {
    add_secondary_separator "Download Upgrade Images on Worker Nodes"
    ssh_all_nodes 'source /var/tmp/functions.sh && 
    pull_image '"$CORTX_SERVER_IMAGE"' &&
    pull_image '"$CORTX_DATA_IMAGE"' &&
    pull_image '"$CORTX_CONTROL_IMAGE"''
}

function upgrade_cluster() {
    if [ "$SOLUTION_CONFIG_TYPE" == manual ]; then
        scp_primary_node $SOLUTION_CONFIG
    fi
    add_primary_separator "\tUpgrading CORTX Cluster"
    setup_worker_nodes 
    ssh_primary_node 'source /var/tmp/functions.sh &&
    if [ '"$SOLUTION_CONFIG_TYPE"' == "manual" ]; then copy_solution_config '"$REMOTE_SOLUTION_CONFIG"' '"$SCRIPT_LOCATION"'; fi &&
    add_secondary_separator "Download Upgrade Images on Primary Node" && 
    CORTX_ACTUAL_SERVER_IMAGE=`pull_image '"$CORTX_SERVER_IMAGE"'` &&
    echo '"CORTX_ACTUAL_SERVER_IMAGE : \$CORTX_ACTUAL_SERVER_IMAGE"' &&
    CORTX_ACTUAL_DATA_IMAGE=`pull_image '"$CORTX_DATA_IMAGE"'` &&
    echo '"CORTX_ACTUAL_DATA_IMAGE : \$CORTX_ACTUAL_DATA_IMAGE"' &&
    CORTX_ACTUAL_CONTROL_IMAGE=`pull_image '"$CORTX_CONTROL_IMAGE"'` &&
    echo '"CORTX_ACTUAL_CONTROL_IMAGE : \$CORTX_ACTUAL_CONTROL_IMAGE"' &&
    add_secondary_separator "Updating CORTX Images info in solution.yaml" &&
    imageArray=( '"\$CORTX_ACTUAL_CONTROL_IMAGE"' '"\$CORTX_ACTUAL_DATA_IMAGE"' '"\$CORTX_ACTUAL_SERVER_IMAGE"' '"\$CORTX_ACTUAL_CONTROL_IMAGE"' '"\$CORTX_ACTUAL_DATA_IMAGE"' ) &&
    servicesArray=( "control-pod" "data-pod" "server-pod" "ha-pod" "client-pod" ) &&
    for i in "${!imageArray[@]}"; do update_image "${servicesArray[i]}" "${imageArray[i]}"; done &&
    add_secondary_separator "Begin CORTX Cluster Upgrade" &&
    pushd '"$SCRIPT_LOCATION"' &&
    if [ '"$UPGRADE_TYPE"' == "rolling-upgrade" ]; then ./upgrade-cortx-cloud.sh start -p '"$POD_TYPE"'; else ./upgrade-cortx-cloud.sh -cold; fi &&
    popd' | tee /var/tmp/upgrade-logs.txt    
}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

if [ "$SOLUTION_CONFIG_TYPE" == manual ]; then
    SOLUTION_CONFIG="$PWD/solution.yaml"
    if [ ! -f "$SOLUTION_CONFIG" ]; then echo -e "ERROR:$SOLUTION_CONFIG file is not available..."; exit 1; fi
fi
validation
generate_rsa_key
nodes_setup
scp_all_nodes functions.sh
scp_primary_node cortx-deploy-functions.sh functions.sh io-sanity.sh


case $ACTION in
    --upgrade)
        check_params
        upgrade_cluster
    ;;
    --suspend)
        suspend_cluster_upgrade
    ;;
    --resume)
        resume_cluster_upgrade
    ;;
    --upgrade-status)
        get_cluster_upgrade_status
    ;;
    --cluster-status)
        check_cluster_status
    ;;
    --io-sanity)
        check_io_operations
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;
esac