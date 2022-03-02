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

set -eo pipefail

SCRIPT_LOCATION="/root/deploy-scripts/cortx-prvsnr"
PRVSNR_SCRIPTS="test/deploy/kubernetes"

function usage(){
    cat << HEREDOC
Usage : $0 [--setup-primary, --setup-worker, --upgrade, --destroy]
where,
    --setup-primary - Setup primary node for CORTX deployment
    --setup-worker - Setup worker node for CORTX deployment
    --upgrade - Upgrade CORTX cluster
    --destroy  - Destroy CORTX cluster
HEREDOC
}

function download_prvsnr_script(){
    if [ -z "$SCRIPT_LOCATION" ]; then echo "SCRIPT_LOCATION not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_PRVSNR_REPO" ]; then echo "CORTX_PRVSNR_REPO not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_PRVSNR_BRANCH" ]; then echo "CORTX_PRVSNR_BRANCH not provided.Exiting..."; exit 1; fi

    rm -rf $SCRIPT_LOCATION
    yum install git -y
    git clone https://github.com/$CORTX_PRVSNR_REPO $SCRIPT_LOCATION
    pushd $SCRIPT_LOCATION
    git checkout $CORTX_PRVSNR_BRANCH
    popd
}

function setup_worker_node(){
    download_prvsnr_script
    pushd $SCRIPT_LOCATION/$PRVSNR_SCRIPTS
        echo "---------------------------------------[ Prerequisite for Deployment ]----------------------------------"
        ./reimage.sh
    popd

}

function setup_primary_node(){
    download_prvsnr_script
    pushd $SCRIPT_LOCATION/$PRVSNR_SCRIPTS
        echo "---------------------------------------[ Prerequisite for Deployment ]----------------------------------"
        ./reimage.sh
        echo "---------------------------------------[ Deploy Provisioner Deployment POD ]----------------------------------"
        ./deploy.sh -i $CORTX_IMAGE -r
    popd
}

function upgrade_cluster(){
    download_prvsnr_script
    pushd $SCRIPT_LOCATION/$PRVSNR_SCRIPTS
        echo "---------------------------------------[ Upgrade Provisioner Deployment POD ]----------------------------------"
        ./upgrade.sh -i $CORTX_IMAGE -r
    popd

}

function destroy_cluster(){
   if [ "$(/usr/bin/kubectl get pods --no-headers | wc -l)" -gt 0 ]; then 
        pushd $SCRIPT_LOCATION/$PRVSNR_SCRIPTS
            ./destroy.sh
        popd || exit
    else 
        echo "CORTX Cluster is not already deployed."
    fi
}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

case $ACTION in
    --setup-primary)
        setup_primary_node        
    ;;
    --setup-worker)
        setup_worker_node        
    ;;
    --upgrade)
        upgrade_cluster
    ;;
    --destroy)
        destroy_cluster
    ;;
    *)
        echo "ERROR : Please provide valid option"
        usage
        exit 1
    ;;
esac
