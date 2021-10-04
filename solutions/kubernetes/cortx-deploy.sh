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

function setup_cluster {
    ALL_NODES=$(cat "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
    MASTER_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
    WORKER_NODES=$(cat "$HOST_FILE" | grep -v "$MASTER_NODE" | awk -F[,] '{print $1}' | cut -d'=' -f2)

    echo "MASTER NODE:" $MASTER_NODE
    echo "WORKER NODE:" $WORKER_NODES

    for node in $ALL_NODES
	do 
	scp -q cortx-deploy-functions.sh functions.sh "$node":/var/tmp/
	done

    for worker_node in $WORKER_NODES
	do
	ssh -o 'StrictHostKeyChecking=no' "$worker_node" "/var/tmp/cortx-deploy-functions.sh --worker"
	done


    for master_node in $MASTER_NODE
	do
	ssh -o 'StrictHostKeyChecking=no' "$master_node" "export GITHUB_TOKEN=$GITHUB_TOKEN && /var/tmp/cortx-deploy-functions.sh --master"
        done

   
}

validation
nodes_setup
setup_cluster
