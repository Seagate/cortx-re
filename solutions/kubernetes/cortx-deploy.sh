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

source functions.sh

HOST_FILE=$PWD/hosts
SSH_KEY_FILE=/root/.ssh/id_rsa

function usage(){
    cat << HEREDOC
Usage : $0 [--third-party, --cortx-cluster, --destroy-cluster, --io-test]
where,
    --third-party - Deploy third-party components
    --cortx-cluster - Deploy Third-Party and CORTX components
    --destroy-cluster  - Destroy CORTX cluster
    --io-test - Perform IO sanity test
HEREDOC
}

function check_params {
    if [ -z "$CORTX_SCRIPTS_REPO" ]; then echo "CORTX_SCRIPTS_REPO not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_SCRIPTS_BRANCH" ]; then echo "CORTX_SCRIPTS_BRANCH not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_IMAGE" ]; then echo "CORTX_IMAGE not provided.Exiting..."; exit 1; fi
    if [ -z "$SOLUTION_CONFIG_TYPE" ]; then echo "SOLUTION_CONFIG_TYPE not provided.Exiting..."; exit 1; fi
    if [ -z "$SNS_CONFIG" ]; then SNS_CONFIG="1+0+0"; fi
    if [ -z "$DIX_CONFIG" ]; then DIX_CONFIG="1+0+0"; fi
}

function pdsh_worker_exec {
    # commands to run in parallel on pdsh hosts (workers nodes).
    commands=(
       "export CORTX_IMAGE=$CORTX_IMAGE && export CORTX_SCRIPTS_REPO=$CORTX_SCRIPTS_REPO && export CORTX_SCRIPTS_BRANCH=$CORTX_SCRIPTS_BRANCH && /var/tmp/cortx-deploy-functions.sh --setup-worker"
    )
    for cmds in "${commands[@]}"; do
       pdsh -w ^$1 $cmds
    done
}

function setup_cluster {
	
   echo  "Using $SOLUTION_CONFIG_TYPE type for generating solution.yaml"

    if [ "$SOLUTION_CONFIG_TYPE" == manual ]; then
        SOLUTION_CONFIG="$PWD/solution.yaml"
        if [ -f '$SOLUTION_CONFIG' ]; then echo "file $SOLUTION_CONFIG not available..."; exit 1; fi
    fi

    validation
    generate_rsa_key
    nodes_setup

    TARGET=$1
    ALL_NODES=$(cat "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
    PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)

    for node in $ALL_NODES
        do
            scp -q cortx-deploy-functions.sh functions.sh $SOLUTION_CONFIG "$node":/var/tmp/
        done
     
    if [ "$(wc -l < $HOST_FILE)" == "1" ]; then
       echo "---------------------------------------[ Single node deployment ]----------------------------------"
       echo "NODE:" $PRIMARY_NODE
       ssh -o 'StrictHostKeyChecking=no' "$PRIMARY_NODE" "export SOLUTION_CONFIG_TYPE=$SOLUTION_CONFIG_TYPE && export CORTX_IMAGE=$CORTX_IMAGE && export CORTX_SCRIPTS_REPO=$CORTX_SCRIPTS_REPO && export CORTX_SCRIPTS_BRANCH=$CORTX_SCRIPTS_BRANCH && export SNS_CONFIG=$SNS_CONFIG && export DIX_CONFIG=$DIX_CONFIG && /var/tmp/cortx-deploy-functions.sh --setup-primary"
    else
       WORKER_NODES=$(cat "$HOST_FILE" | grep -v "$PRIMARY_NODE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
    fi

    # Setup primary node and all worker nodes in parallel in case of multinode cluster.
    if [ "$(wc -l < $HOST_FILE)" -ne "1" ]; then
       NODES=$(wc -l < $HOST_FILE)
       TAINTED_NODES=$(ssh -o 'StrictHostKeyChecking=no' "$PRIMARY_NODE" bash << EOF
kubectl get nodes -o jsonpath="{range .items[*]}{.metadata.name} {.spec.taints[?(@.effect=='NoSchedule')].effect}{\"\n\"}{end}" | grep  NoSchedule | wc -l
EOF
)
       NODES="$((NODES-TAINTED_NODES))"
       echo "---------------------------------------[ $NODES node deployment ]----------------------------------"
       echo "PRIMARY NODE:" $PRIMARY_NODE
       echo "WORKER NODE:" $WORKER_NODES
       # pdsh hosts to run parallel implementations.
       echo $WORKER_NODES > /var/tmp/pdsh-hosts
       pdsh_worker_exec /var/tmp/pdsh-hosts

       for primary_node in $PRIMARY_NODE
           do
               ssh -o 'StrictHostKeyChecking=no' "$primary_node" "export SOLUTION_CONFIG_TYPE=$SOLUTION_CONFIG_TYPE && export CORTX_IMAGE=$CORTX_IMAGE && export CORTX_SCRIPTS_REPO=$CORTX_SCRIPTS_REPO && export CORTX_SCRIPTS_BRANCH=$CORTX_SCRIPTS_BRANCH && export SNS_CONFIG=$SNS_CONFIG && export DIX_CONFIG=$DIX_CONFIG && /var/tmp/cortx-deploy-functions.sh --setup-primary"
           done
    fi

    for primary_node in $PRIMARY_NODE
        do
        ssh -o 'StrictHostKeyChecking=no' "$primary_node" "/var/tmp/cortx-deploy-functions.sh --$TARGET"
        echo "---------------------------------------[ Print Cluster Status ]----------------------------------------------"
        rm -rf /var/tmp/cortx-cluster-status.txt
        ssh -o 'StrictHostKeyChecking=no' "$primary_node" '/var/tmp/cortx-deploy-functions.sh --status' | tee /var/tmp/cortx-cluster-status.txt
        done
}

function support_bundle(){
    PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
    echo -e "\n---------------------------------------[ Collect CORTX Support Bundle Logs ]----------------------------------------------"
    ssh -o 'StrictHostKeyChecking=no' "$PRIMARY_NODE" '/var/tmp/cortx-deploy-functions.sh --generate-logs'
}

function destroy-cluster(){
    validation
    generate_rsa_key
    nodes_setup
	PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
	echo "---------------------------------------[ Destroying cluster from $PRIMARY_NODE ]----------------------------------------------"
        scp -q cortx-deploy-functions.sh functions.sh "$PRIMARY_NODE":/var/tmp/
        ssh -o 'StrictHostKeyChecking=no' "$PRIMARY_NODE" "/var/tmp/cortx-deploy-functions.sh --destroy"
        echo "--------------------------------[ Print Kubernetes Cluster Status after Cleanup]----------------------------------------------"
        ssh -o 'StrictHostKeyChecking=no' "$PRIMARY_NODE" 'kubectl get pods -o wide' | tee /var/tmp/cortx-cluster-status.txt	
}

function io-test(){

    if [ "$SOLUTION_CONFIG_TYPE" == manual ]; then
        SOLUTION_CONFIG="$PWD/solution.yaml"
        if [ -f '$SOLUTION_CONFIG' ]; then echo "file $SOLUTION_CONFIG not available..."; exit 1; fi
    fi

    validation
    generate_rsa_key
    nodes_setup
    PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
    for primary_node in $PRIMARY_NODE
        do
        # IO test
        add_separator Setting up IO testing
        scp -q io-testing.sh s3-client-setup.sh "$primary_node":/var/tmp/
        ssh -o 'StrictHostKeyChecking=no' "$primary_node" "export DEPLOYMENT_TYPE=$DEPLOYMENT_TYPE && /var/tmp/cortx-deploy-functions.sh --io-test"
        done
}


ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi


case $ACTION in
    --third-party)
        check_params
        setup_cluster third-party        
    ;;
    --cortx-cluster)
        check_params
        setup_cluster cortx-cluster
    ;;
    --support-bundle)
        support_bundle
    ;;
    --destroy-cluster)
        destroy-cluster
    ;;
    --io-test)
        io-test
    ;;
    *)
        echo "ERROR : Please provide valid option"
        usage
        exit 1
    ;;
esac
