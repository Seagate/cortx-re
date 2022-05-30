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

source ../../solutions/kubernetes/functions.sh

#variables
HOST_FILE=$PWD/hosts
SSH_KEY_FILE=/root/.ssh/id_rsa
ALL_NODES=$(cat "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)

    validation
    generate_rsa_key
    nodes_setup
    scp_primary_node ../../solutions/kubernetes/functions.sh whitesource_container_scan_function.sh
    ssh_primary_node "
    export SOLUTION_CONFIG_TYPE=automated && 
    export WHITESOURCE_SERVER_URL=$WHITESOURCE_SERVER_URL && 
    export API_KEY=$API_KEY && 
    export USER_KEY=$USER_KEY && 
    export PRODUCT_NAME=$PRODUCT_NAME && 
    export DOCKER_REGISTRY=$DOCKER_REGISTRY && 
    export WHITESOURCE_VERSION=$WHITESOURCE_VERSION && 
    export PULL_SECRET=$PULL_SECRET && /var/tmp/whitesource_container_scan_function.sh"
