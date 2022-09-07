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

HOST_FILE=$PWD/hosts
SSH_KEY_FILE=/root/.ssh/id_rsa
ALL_NODES=$(cat "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
WORKER_NODES=$(cat "$HOST_FILE" | grep -v "$PRIMARY_NODE" | awk -F[,] '{print $1}' | cut -d'=' -f2) || true
CEPH_DEPLOYMENT="false"
SCRIPT_PATH="$(readlink -f "$0")"
SCRIPT_DIR="${SCRIPT_PATH%/*}"

function usage() {
    cat << HEREDOC
Usage : $0 [--deploy-perfline, --perfline-workloads]
where,
    --deploy-perfline - Deploy Perfline on provided nodes.
    --perfline-workloads - Perform pre-defined perfline workloads.
HEREDOC
}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

function check_params() {
    if [ -z "$SEAGATE_TOOLS_REPO" ]; then echo "SEAGATE-TOOLS Repo not provided. Using default: Seagate/seagate-tools"; SEAGATE_TOOLS_REPO="Seagate/seagate-tools"; fi
    if [ -z "$PERFLINE_WORKLOADS_DIR" ]; then echo "Perfline pre-defined workload directory"; PERFLINE_WORKLOADS_DIR="workload/jenkins"; fi
    if [ -z "$CORTX_SCRIPTS_REPO" ]; then echo "CORTX_SCRIPTS_REPO not provided. Using default: Seagate/cortx-k8s ";CORTX_SCRIPTS_REPO="Seagate/cortx-k8s"; fi
    if [ -z "$CORTX_SCRIPTS_BRANCH" ]; then echo "CORTX_SCRIPTS_BRANCH not provided. Using default: v0.12.0";CORTX_SCRIPTS_BRANCH="v0.12.0"; fi
    if [ -z "$SEAGATE_TOOLS_BRANCH" ]; then echo "PERFLINE_VERSION not provided. Using defautl: perfline_v0.4.0"; SEAGATE_TOOLS_BRANCH="perfline_v0.4.0"; fi
    if [ -z "$CORTX_SERVER_IMAGE" ]; then echo "CORTX_SERVER_IMAGE not provided. Using default : ghcr.io/seagate/cortx-rgw:2.0.0-latest"; CORTX_SERVER_IMAGE=ghcr.io/seagate/cortx-rgw:2.0.0-latest; fi
    if [ -z "$CORTX_DATA_IMAGE" ]; then echo "CORTX_DATA_IMAGE not provided. Using default : ghcr.io/seagate/cortx-data:2.0.0-latest"; CORTX_DATA_IMAGE=ghcr.io/seagate/cortx-data:2.0.0-latest; fi
    if [ -z "$CORTX_CONTROL_IMAGE" ]; then echo "CORTX_CONTROL_IMAGE not provided. Using default : ghcr.io/seagate/cortx-control:2.0.0-latest"; CORTX_CONTROL_IMAGE=ghcr.io/seagate/cortx-control:2.0.0-latest; fi
    if [ -z "$DEPLOYMENT_METHOD" ]; then echo "DEPLOYMENT_METHOD not provided. Using default : standard"; DEPLOYMENT_METHOD=standard; fi
    if [ -z "$SOLUTION_CONFIG_TYPE" ]; then echo "SOLUTION_CONFIG_TYPE not provided. Using default : manual"; SOLUTION_CONFIG_TYPE=manual; fi
    if [ -z "$SNS_CONFIG" ]; then SNS_CONFIG="1+0+0"; fi
    if [ -z "$DIX_CONFIG" ]; then DIX_CONFIG="1+0+0"; fi
    if [ -z "$EXTERNAL_EXPOSURE_SERVICE" ]; then EXTERNAL_EXPOSURE_SERVICE="NodePort"; fi
    if [ -z "$CONTROL_EXTERNAL_NODEPORT" ]; then CONTROL_EXTERNAL_NODEPORT="31169"; fi
    if [ -z "$S3_EXTERNAL_HTTP_NODEPORT" ]; then S3_EXTERNAL_HTTP_NODEPORT="30080"; fi
    if [ -z "$S3_EXTERNAL_HTTPS_NODEPORT" ]; then S3_EXTERNAL_HTTPS_NODEPORT="30443"; fi
    if [ -z "$SYSTEM_DRIVE" ]; then echo "SYSTEM_DRIVE not provided. Using default: /dev/sdb";SYSTEM_DRIVE="/dev/sdb"; fi
    if [ -z "$NAMESPACE" ]; then echo "NAMESPACE not provided. Using default: default";NAMESPACE="default"; fi
    if [ -z "$COMMUNITY_USE" ]; then COMMUNITY_USE="no"; fi

   echo -e "\n\n########################################################################"
   echo -e "# SEAGATE_TOOLS_REPO         : $SEAGATE_TOOLS_REPO                  "
   echo -e "# SEAGATE_TOOLS_BRANCH       : $SEAGATE_TOOLS_BRANCH                "
   echo -e "# PERFLINE_WORKLOADS_DIR     : $PERFLINE_WORKLOADS_DIR              "
   echo -e "# CORTX_SCRIPTS_REPO         : $CORTX_SCRIPTS_REPO                  "
   echo -e "# CORTX_SCRIPTS_BRANCH       : $CORTX_SCRIPTS_BRANCH                "
   echo -e "# CORTX_SERVER_IMAGE         : $CORTX_SERVER_IMAGE                  "
   echo -e "# CORTX_DATA_IMAGE           : $CORTX_DATA_IMAGE                    "
   echo -e "# CORTX_CONTROL_IMAGE        : $CORTX_CONTROL_IMAGE                 "
   echo -e "# DEPLOYMENT_METHOD          : $DEPLOYMENT_METHOD                   "
   echo -e "# SOLUTION_CONFIG_TYPE       : $SOLUTION_CONFIG_TYPE                "
   echo -e "# SNS_CONFIG                 : $SNS_CONFIG                          "
   echo -e "# DIX_CONFIG                 : $DIX_CONFIG                          "
   echo -e "# EXTERNAL_EXPOSURE_SERVICE  : $EXTERNAL_EXPOSURE_SERVICE           "
   echo -e "# CONTROL_EXTERNAL_NODEPORT  : $CONTROL_EXTERNAL_NODEPORT           "
   echo -e "# S3_EXTERNAL_HTTP_NODEPORT  : $S3_EXTERNAL_HTTP_NODEPORT           "
   echo -e "# S3_EXTERNAL_HTTPS_NODEPORT : $S3_EXTERNAL_HTTPS_NODEPORT          "
   echo -e "# SYSTEM_DRIVE               : $SYSTEM_DRIVE                        "
   echo -e "# NAMESPACE                  : $NAMESPACE                           "
   echo -e "# COMMUNITY_USE              : $COMMUNITY_USE                       "
   echo -e "#########################################################################"
}

function pdsh_worker_exec() {
    # Commands to run in parallel on pdsh hosts (workers nodes).
    commands=(
       "export CORTX_SERVER_IMAGE=$CORTX_SERVER_IMAGE &&
        export CORTX_DATA_IMAGE=$CORTX_DATA_IMAGE &&
        export CORTX_CONTROL_IMAGE=$CORTX_CONTROL_IMAGE &&
        export CORTX_SCRIPTS_REPO=$CORTX_SCRIPTS_REPO &&
        export CORTX_SCRIPTS_BRANCH=$CORTX_SCRIPTS_BRANCH &&
        export SYSTEM_DRIVE=$SYSTEM_DRIVE &&
        export COMMUNITY_USE=$COMMUNITY_USE && /var/tmp/cortx-deploy-functions.sh --setup-worker"
    )
    for cmds in "${commands[@]}"; do
       pdsh -S -w ^$1 $cmds
       check_status
    done
}

function setup_cluster() {
    add_primary_separator "\t Installation of PerfLine setup on CORTX Cluster"
    add_secondary_separator "Using $SOLUTION_CONFIG_TYPE type for generating solution.yaml"

    if [ "$SOLUTION_CONFIG_TYPE" == manual ]; then
        SOLUTION_CONFIG="$PWD/solution.yaml"
        if [ ! -f "$SOLUTION_CONFIG" ]; then echo -e "ERROR:$SOLUTION_CONFIG file is not available..."; exit 1; fi
    fi

    validation
    generate_rsa_key
    nodes_setup
    cortx_deployment_type

    pushd $SCRIPT_DIR/../../solutions/kubernetes
    scp_all_nodes  cortx-deploy-functions.sh functions.sh $SOLUTION_CONFIG
    popd
    scp_all_nodes install-perfline.sh $SOLUTION_CONFIG

    add_secondary_separator "Setup primary node $PRIMARY_NODE"
    ssh_primary_node "
    export SOLUTION_CONFIG_TYPE=$SOLUTION_CONFIG_TYPE &&
    export CORTX_SERVER_IMAGE=$CORTX_SERVER_IMAGE &&
    export CORTX_DATA_IMAGE=$CORTX_DATA_IMAGE &&
    export CORTX_CONTROL_IMAGE=$CORTX_CONTROL_IMAGE &&
    export DEPLOYMENT_METHOD=$DEPLOYMENT_METHOD &&
    export CORTX_SCRIPTS_REPO=$CORTX_SCRIPTS_REPO &&
    export CORTX_SCRIPTS_BRANCH=$CORTX_SCRIPTS_BRANCH &&
    export SNS_CONFIG=$SNS_CONFIG &&
    export DIX_CONFIG=$DIX_CONFIG &&
    export SYSTEM_DRIVE=$SYSTEM_DRIVE &&
    export CONTROL_EXTERNAL_NODEPORT=$CONTROL_EXTERNAL_NODEPORT &&
    export S3_EXTERNAL_HTTP_NODEPORT=$S3_EXTERNAL_HTTP_NODEPORT &&
    export S3_EXTERNAL_HTTPS_NODEPORT=$S3_EXTERNAL_HTTPS_NODEPORT &&
    export NAMESPACE=$NAMESPACE &&
    export EXTERNAL_EXPOSURE_SERVICE=$EXTERNAL_EXPOSURE_SERVICE &&
    export COMMUNITY_USE=$COMMUNITY_USE && /var/tmp/cortx-deploy-functions.sh --setup-primary"

    if [ "$SINGLE_NODE_DEPLOYMENT" == "False" ]; then
        # pdsh hosts to run parallel implementations on worker nodes.
        echo $WORKER_NODES > /var/tmp/pdsh-hosts
        add_secondary_separator "Setup worker nodes parallely"
        pdsh_worker_exec /var/tmp/pdsh-hosts
    fi

    # Deploy Perfline CLuster (ansible-playbook -i inventories/perfline_hosts/hosts run_perfline.yml -v) :
    ssh_primary_node "
    export SEAGATE_TOOLS_REPO=$SEAGATE_TOOLS_REPO &&
    export SEAGATE_TOOLS_BRANCH=$SEAGATE_TOOLS_BRANCH &&
    export SYSTEM_DRIVE=$SYSTEM_DRIVE &&
    export WORKLOADS_DIR=$PERFLINE_WORKLOADS_DIR &&
    /var/tmp/install-perfline.sh --install-perfline"

    add_primary_separator "\tPrint Perfline Status"
    rm -rf /var/tmp/perfline-status.txt
    ssh_primary_node "systemctl status perfline" | tee /var/tmp/perfline-status.txt
    add_primary_separator "\tPrint Perfline-ui Status"
    rm -rf /var/tmp/perfline-ui-status.txt
    ssh_primary_node "systemctl status perfline-ui" | tee /var/tmp/perfline-ui-status.txt
}

function perfline-workloads() {
    if [ "$SOLUTION_CONFIG_TYPE" == manual ]; then
        SOLUTION_CONFIG="$PWD/solution.yaml"
        if [ -f '$SOLUTION_CONFIG' ]; then echo "file $SOLUTION_CONFIG not available..."; exit 1; fi
    fi
    add_primary_separator "Trigger perfline workloads"
    add_secondary_separator "http://$PRIMARY_NODE:8005/#!/results"

    ssh_primary_node "
    export WORKLOADS_DIR=$PERFLINE_WORKLOADS_DIR &&
    /var/tmp/install-perfline.sh --run-workloads"

}

case $ACTION in
    --deploy-perfline)
        check_params
        setup_cluster
    ;;
    --perfline-workloads)
        perfline-workloads
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;
esac
