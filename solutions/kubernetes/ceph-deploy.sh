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

function usage() {
    cat << HEREDOC
Usage : $0 [--install-pereq, --install-ceph, --deploy-prereq, --deploy-mon, --deploy-mgr, --deploy-osd, --deploy-mds, --deploy-fs, --deploy-rgw, --io-operation]
where,
    --install-prereq - Install Ceph Dependencies before installing ceph packages.
    --install-ceph - Install Ceph Packages.
    --deploy-prereq - Prerquisites before Ceph Deployemnt on cluster nodes required for passwordless ssh to copy ceph configuration and keys.
    --deploy-mon - Deploy Ceph Monitor daemon on primary node.
    --deploy-mgr - Deploy Ceph Manager daemon on primary node.
    --deploy-osd - Deploy Ceph OSD daemon on all nodes.
    --deploy-mds - Deploy Ceph Metadata Service daemon on primary node.
    --deploy-fs - Deploy Ceph FS daemon on primary node.
    --deploy-rgw - Deploy Ceph Rados Gateway daemon on primary node.
    --io-operation - Perform IO operation.
HEREDOC
}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

function install_prereq() {
    validation
    generate_rsa_key
    nodes_setup

    add_primary_separator "\tInstall dependencies for Ceph"
    scp_all_nodes functions.sh ceph-deploy-functions.sh hosts

    echo $ALL_NODES > /var/tmp/pdsh-hosts
    pdsh -S -w ^/var/tmp/pdsh-hosts "/var/tmp/ceph-deploy-functions.sh --install-prereq"
    check_status
}

function install_ceph() {
    add_primary_separator "\t\tInstall Ceph Packages"
    pdsh -w ^/var/tmp/pdsh-hosts "/var/tmp/ceph-deploy-functions.sh --install-ceph"
}

function deploy_prereq() {
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
    ssh_primary_node "export HOST_FILE=/var/tmp/hosts && export SSH_KEY_FILE=$SSH_KEY_FILE && /var/tmp/ceph-deploy-functions.sh --deploy-prereq"
}

function deploy_mon() {
    add_primary_separator "\tDeploy Ceph Monitor Daemon"
    ssh_primary_node "/var/tmp/ceph-deploy-functions.sh --deploy-mon"
}

function deploy_mgr() {
    add_primary_separator "\tDeploy Ceph Manager Daemon"
    ssh_primary_node "/var/tmp/ceph-deploy-functions.sh --deploy-mgr"
}

function deploy_osd() {
    add_primary_separator "\t\tDeploy Ceph OSD"
    ssh_primary_node "export HOST_FILE=/var/tmp/hosts && export SSH_KEY_FILE=$SSH_KEY_FILE && export OSD_DISKS=/var/tmp/osd_disks && /var/tmp/ceph-deploy-functions.sh --deploy-osd"
}

function deploy_mds() {
    add_primary_separator "\tDeploy Ceph Metadata Service Daemon"
    ssh_primary_node "/var/tmp/ceph-deploy-functions.sh --deploy-mds"
}

function deploy_fs() {
    add_primary_separator "\tDeploy Ceph FS and pool"
    ssh_primary_node "/var/tmp/ceph-deploy-functions.sh --deploy-fs"
}

function deploy_rgw() {
    add_primary_separator "\tDeploy Ceph RADOS Gateway"
    ssh_primary_node "/var/tmp/ceph-deploy-functions.sh --deploy-rgw"
}

function io_operation() {
    add_primary_separator "\tPerform IO Operation"
    ssh_primary_node "/var/tmp/ceph-deploy-functions.sh --io-operation"
}

case $ACTION in
    --install-prereq)
        install_prereq
    ;;
    --install-ceph)
        install_ceph
    ;;
    --deploy-prereq)
        deploy_prereq
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
    --io-operation)
        io_operation
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;
esac