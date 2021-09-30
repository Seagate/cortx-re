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
GITHUB_TOKEN=$1
SYSTESM_DRIVE="/dev/sdg"

#download CORTX k8 deployment scripts
#format and mount system drive
#modify solution.yaml
#glusterfes requirements
#openldap requirements
#execute script

function download_deploy_script(){
yum install git -y 
git clone https://$GITHUB_TOKEN@github.com/Seagate/cortx-k8s/ -b UDX-5986_cortxProvisioner_cortxData_with_dummy_containers
}

function update_solution_config(){

 for ssh_node in $(cat "$HOST_FILE")
    do
        local NODE=$(echo "$ssh_node" | awk -F[,] '{print $1}' | cut -d'=' -f2)

        echo "----------------------[ Setting up passwordless ssh for $NODE ]--------------------------------------"
    done

pushd cortx-k8s/k8_cortx_cloud
cat solution.yaml

popd

}

function mount_system_device(){

mkfs.ext4 $SYSTESM_DRIVE
mkdir -p /mnt/fs-local-volume
mount -t ext4 $SYSTESM_DRIVE /mnt/fs-local-volume
}

function glusterfs_requirements(){

mkdir -p /mnt/fs-local-volume/etc/gluster
mkdir -p /mnt/fs-local-volume/var/log/gluster
mkdir -p /mnt/fs-local-volume/var/lib/glusterd

yum install glusterfs-fuse -y
}

function openldap_requiremenrs(){

mkdir -p /var/lib/ldap
echo "ldap:x:55:" >> /etc/group
echo "ldap:x:55:55:OpenLDAP server:/var/lib/ldap:/sbin/nologin" >> /etc/passwd
chown -R ldap.ldap /var/lib/ldap

}

validation
download_deploy_script
update_solution_config
