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

source functions.sh

HOST_FILE=$PWD/hosts
OSD_DISKS=$PWD/osd_disks
SSH_KEY_FILE=/root/.ssh/id_rsa
ALL_NODES=$(cat "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
WORKER_NODES=$(cat "$HOST_FILE" | grep -v "$PRIMARY_NODE" | awk -F[,] '{print $1}' | cut -d'=' -f2) || true

function usage() {
    cat << HEREDOC
Usage : $0 [--cortx-cluster, --destroy-cluster, --io-sanity, --support-bundle]
where,
    --cortx-cluster - Deploy CORTX Cluster on provided nodes.
    --destroy-cluster  - Destroy CORTX cluster.
    --io-sanity - Perform IO sanity test.
    --support-bundle - Collect support bundle logs.
HEREDOC
}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

function prereq() {
    validation
    generate_rsa_key
    nodes_setup

    if [ ! -f "$OSD_DISKS" ]; then
        echo "$OSD_DISKS is not present"
        exit 1
    fi

    add_secondary_separator "Copy scripts and files to primary ceph monitor node"
    scp_primary_node functions.sh ceph-deploy-functions.sh osd_disks hosts

    add_secondary_separator "Setup passwordless ssh on deployment nodes"
    ssh_primary_node "export HOST_FILE=/var/tmp/hosts && export SSH_KEY_FILE=$SSH_KEY_FILE && /var/tmp/ceph-deploy-functions.sh --prereq"
}

function check_params() {
    if [ -z "$OSD_POOL_DEFAULT_SIZE" ]; then echo "OSD_POOL_DEFAULT_SIZE not provided. Using default: Seagate/cortx-k8s ";OSD_POOL_DEFAULT_SIZE="Seagate/cortx-k8s"; fi
    if [ -z "$OSD_POOL_DEFAULT_MIN_SIZE" ]; then echo "OSD_POOL_DEFAULT_MIN_SIZE not provided. Using default: v0.5.0";OSD_POOL_DEFAULT_MIN_SIZE="v0.5.0"; fi
    if [ -z "$OSD_POOL_DEFAULT_PG_NUM" ]; then echo "OSD_POOL_DEFAULT_PG_NUM not provided. Using default: ghcr.io/seagate/cortx-all:2.0.0-latest"; OSD_POOL_DEFAULT_PG_NUM=ghcr.io/seagate/cortx-all:2.0.0-latest; fi
    if [ -z "$OSD_POOL_DEFAULT_PGP_NUM" ]; then echo "OSD_POOL_DEFAULT_PGP_NUM not provided. Using default : ghcr.io/seagate/cortx-rgw:2.0.0-latest"; OSD_POOL_DEFAULT_PGP_NUM=ghcr.io/seagate/cortx-rgw:2.0.0-latest; fi

   echo -e "\n\n########################################################################"
   echo -e "# OSD_POOL_DEFAULT_SIZE         : $OSD_POOL_DEFAULT_SIZE            "
   echo -e "# OSD_POOL_DEFAULT_MIN_SIZE     : $OSD_POOL_DEFAULT_MIN_SIZE        "
   echo -e "# OSD_POOL_DEFAULT_PG_NUM       : $OSD_POOL_DEFAULT_PG_NUM          "
   echo -e "# OSD_POOL_DEFAULT_PGP_NUM      : $OSD_POOL_DEFAULT_PGP_NUM         "
   echo -e "#########################################################################"
}

function deploy_mon() {
    add_primary_separator "Deploy Ceph Monitor Daemon"
    ssh_primary_node "/var/tmp/ceph-deploy-functions.sh export OSD_POOL_DEFAULT_SIZE=$OSD_POOL_DEFAULT_SIZE && export OSD_POOL_DEFAULT_MIN_SIZE=$OSD_POOL_DEFAULT_MIN_SIZE && export OSD_POOL_DEFAULT_PG_NUM=$OSD_POOL_DEFAULT_PG_NUM && export OSD_POOL_DEFAULT_PGP_NUM=$OSD_POOL_DEFAULT_PGP_NUM && --deploy-mon"
}

function deploy_mgr() {
    add_primary_separator "Deploy Ceph Manager Daemon"
    ssh_primary_node "/var/tmp/ceph-deploy-functions.sh --deploy-mgr"
}

function deploy_osd() {
    add_primary_separator "Deploy Ceph OSD"
    ssh_primary_node "export HOST_FILE=/var/tmp/hosts && export SSH_KEY_FILE=$SSH_KEY_FILE && export OSD_DISKS=/var/tmp/osd_disks && /var/tmp/ceph-deploy-functions.sh --deploy-osd"
}

function deploy_mds() {
    add_primary_separator "Deploy Ceph Metadata Service Daemon"
    ssh_primary_node "/var/tmp/ceph-deploy-functions.sh --deploy-mds"
}

function deploy_fs() {
    add_primary_separator "Deploy Ceph FS and pool"
    ssh_primary_node "/var/tmp/ceph-deploy-functions.sh --deploy-fs"
}

function deploy_rgw() {
    add_primary_separator "Deploy Ceph RADOS Gateway"
    ssh_primary_node "/var/tmp/ceph-deploy-functions.sh --deploy-rgw"
}

case $ACTION in
    --prereq)
        prereq check_params
    ;;
    --deploy-mon)
        deploy_mon
    ;;
    --deploy-mgr)
        deploy_mgr
    ;;
    --deploy-osd)
        deploy_osd
    ;;
    --deploy-mds)
        deploy_mds
    ;;
    --deploy-fs)
        deploy_fs
    ;;
    --deploy-rgw)
        deploy_rgw
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;
esac