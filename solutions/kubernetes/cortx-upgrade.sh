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

set -eo pipefail

source functions.sh cortx-deploy-functions.sh

HOST_FILE="$PWD/hosts"
PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
SSH_KEY_FILE=/root/.ssh/id_rsa
SOLUTION_CONFIG_TYPE="automated"


function usage() {
    cat << HEREDOC
Usage : $0 [--rolling-upgrade, --cold-upgrade,  --suspend, --resume, --status]
where,
    --rolling-upgrade - Perform rolling-upgrade on given CORTX cluster.
    --cold-upgrade  - Perform cold-upgrade on given CORTX cluster.
    --suspend - Suspends current ongoing upgrade and saves the state of upgrade.
    --resume - Resumes the suspended upgrade and continues from the last pod which was being upgraded.
    --status - Displays current state of upgrade.
HEREDOC
}

function check_params() {
    if [ -z "$CORTX_SCRIPTS_REPO" ]; then echo "CORTX_SCRIPTS_REPO is not provided, Using default: Seagate/cortx-k8s "; CORTX_SCRIPTS_REPO="Seagate/cortx-k8s"; fi
    if [ -z "$CORTX_SCRIPTS_BRANCH" ]; then echo "CORTX_SCRIPTS_BRANCH is not provided, Using default: cortx-test"; CORTX_SCRIPTS_BRANCH="cortx-test"; fi
    if [ -z "$CORTX_SERVER_IMAGE" ]; then echo "CORTX_SERVER_IMAGE is not provided, Using default : ghcr.io/seagate/cortx-rgw:2.0.0-latest"; CORTX_SERVER_IMAGE=ghcr.io/seagate/cortx-rgw:2.0.0-latest; fi
    if [ -z "$CORTX_DATA_IMAGE" ]; then echo "CORTX_DATA_IMAGE is not provided, Using default : ghcr.io/seagate/cortx-data:2.0.0-latest"; CORTX_DATA_IMAGE=ghcr.io/seagate/cortx-data:2.0.0-latest; fi
    if [ -z "$CORTX_CONTROL_IMAGE" ]; then echo "CORTX_CONTROL_IMAGE is not provided, Using default : ghcr.io/seagate/cortx-control:2.0.0-latest"; CORTX_CONTROL_IMAGE=ghcr.io/seagate/cortx-control:2.0.0-latest; fi
    if [ -z "$POD_TYPE" ]; then echo "POD_TYPE is not provided, Using default : all"; POD_TYPE="all"; fi

    echo -e "\n\n########################################################################"
    echo -e "# CORTX_SCRIPTS_REPO           : $CORTX_SCRIPTS_REPO                   "
    echo -e "# CORTX_SCRIPTS_BRANCH         : $CORTX_SCRIPTS_BRANCH                 "
    echo -e "# CORTX_SERVER_IMAGE           : $CORTX_SERVER_IMAGE                   "
    echo -e "# CORTX_DATA_IMAGE             : $CORTX_DATA_IMAGE                     "
    echo -e "# CORTX_CONTROL_IMAGE          : $CORTX_CONTROL_IMAGE                  "
    echo -e "# POD_TYPE                     : $POD_TYPE                             "
    echo -e "#########################################################################\n"
}

function upgrade_cluster() {
    UPGRADE_TYPE=$1
    add_primary_separator "\tUpgrading CORTX Cluster"
    validation
    generate_rsa_key
    nodes_setup
    add_secondary_separator "Verifying Pre-Upgrade CORTX Cluster health"
    scp_primary_node cortx-deploy-functions.sh functions.sh
    rm -rf /var/tmp/pre-upgrade-cortx-cluster-status.txt
    ssh_primary_node "export DEPLOYMENT_METHOD="standard" && /var/tmp/cortx-deploy-functions.sh --status" | tee /var/tmp/pre-upgrade-cortx-cluster-status.txt
    add_secondary_separator "Download Upgrade Images"
    pull_image $CORTX_SERVER_IMAGE
    pull_image $CORTX_DATA_IMAGE
    pull_image $CORTX_CONTROL_IMAGE
    add_secondary_separator "Updating CORTX Images info in solution.yaml"   
    update_image control-pod $CORTX_CONTROL_IMAGE
    update_image data-pod $CORTX_DATA_IMAGE
    update_image server-pod $CORTX_SERVER_IMAGE
    update_image ha-pod $CORTX_CONTROL_IMAGE
    update_image client-pod $CORTX_DATA_IMAGE
    add_secondary_separator "Begin CORTX Cluster Upgrade"
    ./upgrade-cortx-cloud.sh start -p $POD_TYPE
    add_secondary_separator "Verifying Post-Upgrade CORTX Cluster health"
    rm -rf /var/tmp/post-upgrade-cortx-cluster-status.txt
    ssh_primary_node "export DEPLOYMENT_METHOD="standard" && /var/tmp/cortx-deploy-functions.sh --status" | tee /var/tmp/post-upgrade-cortx-cluster-status.txt

}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

case $ACTION in
    --rolling-upgrade)
        check_params
        upgrade_cluster "rolling"
    ;;
    --cold-upgrade)
        check_params
        upgrade_cluster "cold"
    ;;
    --suspend)
        suspend_cluster_upgrade
    ;;
    --resume)
        resume_cluster_upgrade
    ;;
    --status)
        get_cluster_upgrade_status
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;
esac