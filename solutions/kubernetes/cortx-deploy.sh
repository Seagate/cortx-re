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
CORTX_SCRIPTS_REPO="https://github.com/Seagate/cortx-k8s/"
CORTX_SCRIPTS_BRANCH="UDX-5986_cortxProvisioner_cortxData_with_dummy_containers"
SYSTESM_DRIVE="/dev/sdg"

#On master
#download CORTX k8 deployment scripts
#modify solution.yaml
#execute script

#On worker
#format and mount system drive
#glusterfes requirements
#openldap requirements

function usage(){
    cat << HEREDOC
Usage : $0 [--worker, --master]
where,
    --worker - Install prerequisites on nodes for kubernetes setup
    --master - Initialize K8 master node. 
HEREDOC
}


#validation
#download_deploy_script
#update_solution_config

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi


function setup_master_node(){
echo "---------------------------------------[ Setting up Master Node ]--------------------------------------"
download_deploy_script $GITHUB_TOKEN
update_solution_config

}


function setup_worker_node(){
echo "---------------------------------------[ Setting up Master Node ]--------------------------------------"
download_deploy_script $GITHUB_TOKEN
update_solution_config

}


case $ACTION in
    --cleanup)
        clenaup_node
    ;;
    --worker) 
        setup_worker_node
    ;;
    --status) 
        print_cluster_status
    ;;
    --master)
        setup_master_node
    ;;
    *)
        echo "ERROR : Please provide valid option"
        usage
        exit 1
    ;;    
esac

