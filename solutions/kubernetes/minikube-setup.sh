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

SSH_KEY_FILE=/root/.ssh/id_rsa
NODE=$1
USER=$2
PASS=$3

function generate_rsa_key {
    if [ ! -f "$SSH_KEY_FILE" ]; then
        ssh-keygen -b 2048 -t rsa -f $SSH_KEY_FILE -q -N ""
     else
        echo $SSH_KEY_FILE already present
    fi
}

function check_status {
    return_code=$?
    error_message=$1
    if [ $return_code -ne 0 ]; then
            echo "----------------------[ ERROR: $error_message ]--------------------------------------"
            exit 1
    fi
    echo "----------------------[ SUCCESS ]--------------------------------------"
}

function nodes_setup {
        echo "----------------------[ Setting up passwordless ssh for $NODE ]--------------------------------------"
        ping -c1 -W1 -q $NODE
        check_status
        sshpass -p "$PASS" ssh-copy-id -f -o StrictHostKeyChecking=no -i ~/.ssh/id_rsa.pub "$USER"@"$NODE"
        check_status "Passwordless ssh setup failed for $NODE. Please validte provide credentails"
        scp -q minikube-functions.sh "$NODE":/var/tmp/
}

function setup_cluster {
    echo "---------------[ Setting up kubernetes cluster on $NODE ]--------------------------------------"
    echo "---------------------------------------[ Cleanup Node ]--------------------------------------"
    ssh -o 'StrictHostKeyChecking=no' "$NODE" '/var/tmp/minikube-functions.sh cleanup'
    check_status
    echo "---------------------------------------[ Preparing Node ]--------------------------------------"
    ssh -o 'StrictHostKeyChecking=no' "$NODE" '/var/tmp/minikube-functions.sh install'
    check_status "Node preparation failed on $NODE"
    echo "---------------------------------------[ Setting up cluster on Node ]--------------------------------------"
    ssh -o 'StrictHostKeyChecking=no' "$NODE" "/var/tmp/minikube-functions.sh setup /usr/local/bin minikube $PASS"
    check_status "Cluster formation failed on $NODE"
}

#Execution
generate_rsa_key
nodes_setup
setup_cluster