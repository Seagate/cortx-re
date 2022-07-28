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

source ../functions.sh

HOST_FILE=$PWD/hosts
SSH_KEY_FILE=/root/.ssh/id_rsa
PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)

function setup_metrics_server() {
    validation
    generate_rsa_key
    nodes_setup

    echo PRIMARY NODE="$PRIMARY_NODE"

    # Copy scripts to all nodes:
    scp_primary_node setup-metrics-server-functions.sh  ../functions.sh ../cluster-functions.sh
    ssh_primary_node "export RAW_CORTX_RE_REPO=$RAW_CORTX_RE_REPO && export CORTX_RE_REPO=$CORTX_RE_REPO && export CORTX_RE_BRANCH=$CORTX_RE_BRANCH && bash /var/tmp/setup-metrics-server-functions.sh"
    check_status
}
function print_status() {
    add_primary_separator "\t\tPrint Node Status"
    rm -rf /var/tmp/cluster-status.txt
    ssh_primary_node '/var/tmp/cluster-functions.sh --status' | tee /var/tmp/cluster-status.txt
}

# Execution
setup_metrics_server
print_status