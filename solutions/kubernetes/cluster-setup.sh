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

HOST_FILE=$PWD/hosts
SSH_KEY_FILE=/root/.ssh/id_rsa

function validation {
    if [ ! -f "$HOST_FILE" ]; then
        echo "$HOST_FILE is not present"
        exit 1
    fi
}

function generate_rsa_key {
    if [ ! -f "$SSH_KEY_FILE" ]; then
        ssh-keygen -b 2048 -t rsa -f $SSH_KEY_FILE -q -N ""
     else
        echo $SSH_KEY_FILE already present
    fi
}

function passwordless_ssh {
    local NODE=$1
    local USER=$2
    local PASS=$3
    sshpass -p "$PASS" ssh-copy-id -f -o StrictHostKeyChecking=no -i ~/.ssh/id_rsa.pub "$USER"@"$NODE"
}

function nodes_setup {
    for ssh_node in $(cat "$HOST_FILE")
    do 
        local NODE=$(echo "$ssh_node" | awk -F[,] '{print $1}' | cut -d'=' -f2)
        local USER=$(echo "$ssh_node" | awk -F[,] '{print $2}' | cut -d'=' -f2)
        local PASS=$(echo "$ssh_node" | awk -F[,] '{print $3}' | cut -d'=' -f2)

        echo "----------------------[ Setting up passwordless ssh for $NODE ]--------------------------------------"
        passwordless_ssh "$NODE" "$USER" "$PASS"
        scp -q cluster-functions.sh "$NODE":/var/tmp/
    done
}

function setup_cluster {
    ALL_NODES=$(cat "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
    MASTER_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
    WORKER_NODES=$(cat "$HOST_FILE" | grep -v "$MASTER_NODE" | awk -F[,] '{print $1}' | cut -d'=' -f2)

    echo "---------------[ Setting up kubernetes cluster for following nodes ]--------------------------------------"
    echo MASTER NODE="$MASTER_NODE"
    echo WORKER NODES="$WORKER_NODES"
    echo "------------------------------------------------------------------------------------------------------"    

    for node in $ALL_NODES
    do 
        echo "---------------------------------------[ Preparing Node $node ]--------------------------------------"
        ssh -o 'StrictHostKeyChecking=no' "$node" '/var/tmp/cluster-functions.sh --prepare'
    done

    echo "---------------------------------------[ Preparing Master Node $MASTER_NODE ]--------------------------------------"
    ssh -o 'StrictHostKeyChecking=no' "$MASTER_NODE" '/var/tmp/cluster-functions.sh --master'
    sleep 10 #To be replaced with status check
    JOIN_COMMAND=$(ssh -o 'StrictHostKeyChecking=no' "$MASTER_NODE" 'kubeadm token create --print-join-command --description "Token to join worker nodes"')

    for worker_node in $WORKER_NODES
        do
        echo "---------------------------------------[ Joining Worker Node $worker_node ]--------------------------------------"
        ssh -o 'StrictHostKeyChecking=no' "$worker_node" "echo "y" | kubeadm reset && $JOIN_COMMAND"
        ssh -o 'StrictHostKeyChecking=no' "$MASTER_NODE" "kubectl label node $worker_node" node-role.kubernetes.io/worker=worker 
    done
}

function print_status {

    echo "---------------------------------------[ Print Node status ]----------------------------------------------"
    sleep 10 #To be replaced with status check
    ssh -o 'StrictHostKeyChecking=no' "$MASTER_NODE" 'kubectl get nodes -o wide'
}

#Execution 
validation
generate_rsa_key
nodes_setup
setup_cluster
print_status
