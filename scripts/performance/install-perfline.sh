#!/bin/bash -x
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
set -euo pipefail
source /var/tmp/functions.sh

HOST_FILE=$(pwd)/hosts
SSH_KEY_FILE=/root/.ssh/id_rsa
ALL_NODES=$(cat "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
PASSWORD=$(head -1 "$HOST_FILE" | awk -F[,] '{print $3}' | cut -d'=' -f2)

Installed_Perfline_Dir="/root/perfline/wrapper"
temp_task="/tmp/task_list.txt"

function usage() {
    cat << HEREDOC
Usage : $0 [--install-perfline, --run-workloads]
where,
    --install-perfline - Deploy Perfline on provided nodes.
    --run-workloads - Perform pre-defined workloads.
HEREDOC
}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

function update_inventory_host()
{
    host_file="$1"
    sed -i 's/srvnode-.*//g' $host_file
    COUNTER=1
    for node in $ALL_NODES;
    do
        sed -i "/\[nodes\]/a srvnode-$COUNTER ansible_host=$node" $host_file
        COUNTER=$((COUNTER+1))
    done
    sed -i "s/client-1.*/client-1 ansible_host=$PRIMARY_NODE/g" $host_file
    sed -i "s/ha_type=.*/ha_type=hare/g" $host_file
    sed -i "s/cluster_pass=.*/cluster_pass=$PASSWORD/g" $host_file
    sed -i "s|cortx_k8s_repo=.*|cortx_k8s_repo=\"/root/deploy-scripts\"|g" $host_file
    sed -i "s|disk=.*|disk=\"$SYSTEM_DRIVE\"|g" $host_file
    echo -e "Successfully Updated Perfline inventory host file"
}

function install_perfline()
{
    local host_file="inventories/perfline_hosts/hosts"
    rm -rf seagate-tools
    git clone --recursive "$SEAGATE_TOOLS_REPO" -b $SEAGATE_TOOLS_BRANCH
    PERFLINE_DIR="seagate-tools/performance/PerfLine"
    pushd "$PERFLINE_DIR"
    update_inventory_host "$host_file"
    ansible-playbook -i "$host_file" run_perfline.yml -v
    popd
}

function trigger_workloads()
{
    rm -rf "$temp_task"
    for workload_file in $(ssh_primary_node ls "$WORKLOADS_DIR")
    do
        ssh_primary_node "cd $Installed_Perfline_Dir; ./perfline.py -a < $WORKLOADS_DIR/$workload_file" >> "$temp_task"
    done
    while read -r line; do
        local task_id=$(echo -e "$line" | awk -F ' ' '{ print $2}' | tr -d "\"|}|,")
        add_primary_separator "\n http://$PRIMARY_NODE:8005/artifacts/$task_id"
    done <"$temp_task"

}

case "$ACTION" in
    --install-perfline)
        install_perfline
    ;;
    --run-workloads)
        trigger_workloads
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;
esac
