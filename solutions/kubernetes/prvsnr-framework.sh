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

source functions.sh

function check_params {
    if [ -z "$CORTX_PRVSNR_REPO" ]; then echo "CORTX_PRVVSNR_REPO not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_PRVSNR_BRANCH" ]; then echo "CORTX_PRVSNR_BRANCH not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_IMAGE" ]; then echo "CORTX_IMAGE not provided.Exiting..."; exit 1; fi
}

function pdsh_worker_exec {
    # commands to run in parallel on pdsh hosts (workers nodes).
    commands=(
       "export CORTX_IMAGE=$CORTX_IMAGE && export CORTX_PRVSNR_REPO=$CORTX_PRVSNR_REPO && export CORTX_PRVSNR_BRANCH=$CORTX_PRVSNR_BRANCH && /var/tmp/prvsnr-framework-functions.sh --setup-worker"
    )
    for cmds in "${commands[@]}"; do
       pdsh -w ^$1 $cmds
    done
}

function setup_cluster(){
    if [ "$SOLUTION_CONFIG_TYPE" == manual ]; then
        SOLUTION_CONFIG="$PWD/solution.yaml"
        if [ -f '$SOLUTION_CONFIG' ]; then echo "file $SOLUTION_CONFIG not available..."; exit 1; fi
    fi

    validation
    generate_rsa_key
    nodes_setup

    ALL_NODES=$(cat "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
    MASTER_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)

    for node in $ALL_NODES
        do
            scp -q cortx-deploy-functions.sh prvsnr-framework-functions.sh "$node":/var/tmp/
        done

    if [ "$(wc -l < $HOST_FILE)" == "1" ]; then
       echo "---------------------------------------[ Single node deployment ]----------------------------------"
       echo "NODE:" $MASTER_NODE
       ssh -o 'StrictHostKeyChecking=no' "$MASTER_NODE" "export CORTX_IMAGE=$CORTX_IMAGE && export CORTX_PRVSNR_REPO=$CORTX_PRVSNR_REPO && export CORTX_PRVSNR_BRANCH=$CORTX_PRVSNR_BRANCH && /var/tmp/prvsnr-framework-functions.sh --setup-master"
    else
       WORKER_NODES=$(cat "$HOST_FILE" | grep -v "$MASTER_NODE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
    fi

    # Setup master node and all worker nodes in parallel in case of multinode cluster.
    if [ "$(wc -l < $HOST_FILE)" -ne "1" ]; then
       NODES=$(wc -l < $HOST_FILE)
       TAINTED_NODES=$(ssh -o 'StrictHostKeyChecking=no' "$MASTER_NODE" bash << EOF
kubectl get nodes -o jsonpath="{range .items[*]}{.metadata.name} {.spec.taints[?(@.effect=='NoSchedule')].effect}{\"\n\"}{end}" | grep  NoSchedule | wc -l
EOF
)
       NODES="$((NODES-TAINTED_NODES))"
       echo "---------------------------------------[ $NODES node deployment ]----------------------------------"
       echo "MASTER NODE:" $MASTER_NODE
       echo "WORKER NODE:" $WORKER_NODES
       echo $WORKER_NODES > /var/tmp/pdsh-hosts
       pdsh_worker_exec /var/tmp/pdsh-hosts

       for master_node in $MASTER_NODE
           do
               ssh -o 'StrictHostKeyChecking=no' "$master_node" "export CORTX_IMAGE=$CORTX_IMAGE && export CORTX_PRVSNR_REPO=$CORTX_PRVSNR_REPO && export CORTX_PRVSNR_BRANCH=$CORTX_PRVSNR_BRANCH && /var/tmp/prvsnr-framework-functions.sh --setup-master"
           done
    fi

    for master_node in $MASTER_NODE
        do
        echo "---------------------------------------[ Print Cluster Status ]----------------------------------------------"
        rm -rf /var/tmp/cortx-cluster-status.txt
        ssh -o 'StrictHostKeyChecking=no' "$master_node" '/var/tmp/cortx-deploy-functions.sh --status' | tee /var/tmp/cortx-cluster-status.txt
        done
}

function upgrade_cluster(){
    if [ "$SOLUTION_CONFIG_TYPE" == manual ]; then
        SOLUTION_CONFIG="$PWD/solution.yaml"
        if [ -f '$SOLUTION_CONFIG' ]; then echo "file $SOLUTION_CONFIG not available..."; exit 1; fi
    fi

    validation
    generate_rsa_key
    nodes_setup

	MASTER_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
	echo "---------------------------------------[ Upgrading cluster from $MASTER_NODE ]----------------------------------------------"
        scp -q cortx-deploy-functions.sh prvsnr-framework-functions.sh "$MASTER_NODE":/var/tmp/
        ssh -o 'StrictHostKeyChecking=no' "$MASTER_NODE" "export CORTX_PRVSNR_REPO=$CORTX_PRVSNR_REPO && export CORTX_PRVSNR_BRANCH=$CORTX_PRVSNR_BRANCH && /var/tmp/prvsnr-framework-functions.sh --upgrade"
        echo "--------------------------------[ Print Cluster Status after Upgrade ]----------------------------------------------"
        rm -rf /var/tmp/cortx-cluster-status.txt
        ssh -o 'StrictHostKeyChecking=no' "$master_node" '/var/tmp/cortx-deploy-functions.sh --status' | tee /var/tmp/cortx-cluster-status.txt
}

function destroy_cluster(){
    if [ "$SOLUTION_CONFIG_TYPE" == manual ]; then
        SOLUTION_CONFIG="$PWD/solution.yaml"
        if [ -f '$SOLUTION_CONFIG' ]; then echo "file $SOLUTION_CONFIG not available..."; exit 1; fi
    fi

    validation
    generate_rsa_key
    nodes_setup

	MASTER_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
	echo "---------------------------------------[ Destroying cluster from $MASTER_NODE ]----------------------------------------------"
        scp -q  prvsnr-framework-functions.sh "$MASTER_NODE":/var/tmp/
        ssh -o 'StrictHostKeyChecking=no' "$MASTER_NODE" "export CORTX_PRVSNR_REPO=$CORTX_PRVSNR_REPO && export CORTX_PRVSNR_BRANCH=$CORTX_PRVSNR_BRANCH && /var/tmp/prvsnr-framework-functions.sh --destroy"
        echo "--------------------------------[ Print Kubernetes Cluster Status after Cleanup]----------------------------------------------"
        ssh -o 'StrictHostKeyChecking=no' "$MASTER_NODE" 'kubectl get pods -o wide' | tee /var/tmp/cortx-cluster-status.txt	
}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

case $ACTION in
    --deploy-cluster)
        check_params
        setup_cluster        
    ;;
    --upgrade-cluster)
        check_params
        upgrade_cluster
    ;;
    --destroy-cluster)
        check_params
        destroy_cluster
    ;;
    *)
        echo "ERROR : Please provide valid option"
        usage
        exit 1
    ;;
esac
