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

function validation {
    if [ ! -f "$HOST_FILE" ]; then
        echo "$HOST_FILE is not present"
        exit 1
    fi
}

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

function passwordless_ssh {
    local NODE=$1
    local USER=$2
    local PASS=$3
    ping -c1 -W1 -q $NODE
    check_status
    sshpass -p "$PASS" ssh-copy-id -f -o StrictHostKeyChecking=no -i ~/.ssh/id_rsa.pub "$USER"@"$NODE"
    check_status "Passwordless ssh setup failed for $NODE. Please validte provide credentails"
}

function nodes_setup {
    for ssh_node in $(cat "$HOST_FILE")
    do
        local NODE=$(echo "$ssh_node" | awk -F[,] '{print $1}' | cut -d'=' -f2)
        local USER=$(echo "$ssh_node" | awk -F[,] '{print $2}' | cut -d'=' -f2)
        local PASS=$(echo "$ssh_node" | awk -F[,] '{print $3}' | cut -d'=' -f2)

        echo "----------------------[ Setting up passwordless ssh for $NODE ]--------------------------------------"
        passwordless_ssh "$NODE" "$USER" "$PASS"
    done
}

function download_deploy_script(){
        SCRIPT_LOCTION="/root/deploy-scripts"
        rm -rf $SCRIPT_LOCTION
	yum install git -y
	git clone https://$GITHUB_TOKEN@github.com/Seagate/cortx-k8s/ -b UDX-5986_cortxProvisioner_cortxData_with_dummy_containers $SCRIPT_LOCTION
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

