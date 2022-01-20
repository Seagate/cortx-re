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

source functions.sh

HOST_FILE=$PWD/hosts
SSH_KEY_FILE=/root/.ssh/id_rsa
UNTAINT="$1"

function setup_cluster {

    validation
    generate_rsa_key
    nodes_setup

    ALL_NODES=$(cat "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
    PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
    WORKER_NODES=$(cat "$HOST_FILE" | grep -v "$PRIMARY_NODE" | awk -F[,] '{print $1}' | cut -d'=' -f2)

    echo "---------------[ Setting up kubernetes cluster for following nodes ]--------------------------------------"
    echo PRIMARY NODE="$PRIMARY_NODE"
    echo WORKER NODES="$WORKER_NODES"
    echo "------------------------------------------------------------------------------------------------------"

    for node in $ALL_NODES
	do 
	    scp -q cluster-functions.sh functions.sh "$node":/var/tmp/
	done

    echo $ALL_NODES > /var/tmp/pdsh-hosts

    # Cleanup nodes:
    pdsh -w ^/var/tmp/pdsh-hosts '/var/tmp/cluster-functions.sh --cleanup'
    # Prepare nodes:
    pdsh -w ^/var/tmp/pdsh-hosts '/var/tmp/cluster-functions.sh --prepare'

    echo "---------------------------------------[ Preparing Primary Node $PRIMARY_NODE ]--------------------------------------"
    ssh -o 'StrictHostKeyChecking=no' "$PRIMARY_NODE" "/var/tmp/cluster-functions.sh --primary ${UNTAINT}"
    check_status
    sleep 10 #To be replaced with status check
    JOIN_COMMAND=$(ssh -o 'StrictHostKeyChecking=no' "$PRIMARY_NODE" 'kubeadm token create --print-join-command --description "Token to join worker nodes"')
    check_status "Failed fetch cluster join command"

    echo $WORKER_NODES > /var/tmp/pdsh-hosts
    # Join worker nodes.
    JOIN_WORKER="/var/tmp/cluster-functions.sh --join-worker-nodes $JOIN_COMMAND"
    pdsh -w ^/var/tmp/pdsh-hosts "$JOIN_WORKER"

    # Label worker nodes.
    for worker_node in $WORKER_NODES
    do
        ssh -o 'StrictHostKeyChecking=no' "$PRIMARY_NODE" "kubectl label node $worker_node" node-role.kubernetes.io/worker=worker
        check_status "Failed to lable $worker_node"
    done
}

function print_status {

    echo "---------------------------------------[ Print Node status ]----------------------------------------------"
    rm -rf /var/tmp/cluster-status.txt
    ssh -o 'StrictHostKeyChecking=no' "$PRIMARY_NODE" '/var/tmp/cluster-functions.sh --status' | tee /var/tmp/cluster-status.txt

    #Clean up known_hosts file entries.
    for node in $ALL_NODES
    do
        sed -i '/'$node'/d' /root/.ssh/known_hosts
    done
}

#Execution
setup_cluster
print_status
