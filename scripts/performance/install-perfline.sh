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

HOST_FILE="/var/tmp/hosts"
SSH_KEY_FILE="/root/.ssh/id_rsa"
ALL_NODES=$(cat "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
PASSWORD=$(head -1 "$HOST_FILE" | awk -F[,] '{print $3}' | cut -d'=' -f2)
PERFLINE_LOG="/var/log/perfline.log"
SCRIPT_LOCATION="/root/seagate-tools"

INSTALLED_PERFLINE_DIR="/root/perfline/wrapper"
temp_task="/var/tmp/perfline_task_list.txt"
task_status="/var/tmp/perfline_task_status.txt"

function usage() {
    cat << HEREDOC
Usage : $0 [--install-perfline, --run-workloads, --final-status]
where,
    --install-perfline - Deploy Perfline on provided nodes.
    --run-workloads    - Perform pre-defined workloads.
    --final-status     - Final status for perfline.
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
        sed -i "/\[client\]/i srvnode-$COUNTER ansible_host=$node" $host_file
        COUNTER=$((COUNTER+1))
    done
    sed -i "s/client-1.*/client-1 ansible_host=$PRIMARY_NODE/g" $host_file
    sed -i "s/ha_type=.*/ha_type=hare/g" $host_file
    sed -i "s/cluster_pass=.*/cluster_pass=$PASSWORD/g" $host_file
    sed -i "s|cortx_k8s_repo=.*|cortx_k8s_repo=\"/root/deploy-scripts\"|g" $host_file
    sed -i "s|disk=.*|disk=\"$SYSTEM_DRIVE\"|g" $host_file
    echo -e "Successfully Updated Perfline inventory host file"
}

function pre_requisites()
{
    if [ $(ls /var/tmp/perfline* | wc -l) -ne 0 ]
    then
        for perfline_file in $(ls /var/tmp/perfline*)
        do
            rm -f $perfline_file
        done
    fi

    if [ -d "$SCRIPT_LOCATION" ]
    then
        rm -rf "$SCRIPT_LOCATION"
    fi

    if [ $(rpm -qa ansible) ]
    then
        echo "Ansible is already installed";
    else
        yum install -y ansible
    fi
}

function install_perfline()
{
    local host_file="inventories/perfline_hosts/hosts"
    git clone "$SEAGATE_TOOLS_REPO" "$SCRIPT_LOCATION"
    pushd $SCRIPT_LOCATION
    git checkout $SEAGATE_TOOLS_BRANCH
    popd

    PERFLINE_DIR="${SCRIPT_LOCATION}/performance/PerfLine"
    pushd "$PERFLINE_DIR"
    update_inventory_host "$host_file"
    ansible-playbook -i "$host_file" run_perfline.yml -v
    popd
}

function trigger_workloads()
{
    for workload_file in $(ssh_primary_node ls "$WORKLOADS_DIR")
    do
        ssh_primary_node "cd $INSTALLED_PERFLINE_DIR; ./perfline.py -a < $WORKLOADS_DIR/$workload_file" >> "$temp_task"
    done

}

function waiting_for_task_completion()
{
    for task_id in $(cat "$temp_task" | awk -F ' ' '{ print $2}' | tr -d "\"|}|,")
    do
        task_list=$(ssh "$PRIMARY_NODE" "cd $INSTALLED_PERFLINE_DIR; ./perfline.py -l")
        sed "/tasks.worker_task: $task_id p=1 executed/q" <(tail -f "$PERFLINE_LOG" )
        if [ $(grep -c "$task_id" <<< "$task_list") -ne 1 ]
        then
            echo -e "Task completed for Task_ID: $task_id"
        fi
    done
}

# Check the status of task
function check_task_status()
{
    for task_id in $(cat "$temp_task" | awk -F ' ' '{ print $2}' | tr -d "\"|}|,")
    do
        task_detail=$(ssh "$PRIMARY_NODE" "cd $INSTALLED_PERFLINE_DIR; ./perfline.py -r" | grep "$task_id")
        if [ $(grep -c '"status": "SUCCESS"' <<< "$task_detail") -eq 1 ]
        then
            echo -e "\n\t $task_id               : SUCCESS" >> "$task_status"
        fi
    done

    if [ -f "$task_status" ]
    then
        for task_id in $(cat "$temp_task" | awk -F ' ' '{ print $2}' | tr -d "\"|}|,")
        do
            echo -e "\n\t <b>Result artifacts</b>: http://$PRIMARY_NODE:8005/#!/artifacts/$task_id \n" >> "$task_status"
        done
        echo -e "\n\t <b>systemctl status perfline</b>" >> "$task_status"
        ssh "$PRIMARY_NODE" systemctl status perfline >> "$task_status"
        echo -e "\n\n\t <b>systemctl status perfline-ui</b>" >> "$task_status"
        ssh "$PRIMARY_NODE" systemctl status perfline-ui >> "$task_status"
        cat "$task_status"
    else
        exit 1
    fi
}

case "$ACTION" in
    --install-perfline)
        pre_requisites
        install_perfline
    ;;
    --run-workloads)
        trigger_workloads
        waiting_for_task_completion
    ;;
    --final-status)
        check_task_status
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;
esac
