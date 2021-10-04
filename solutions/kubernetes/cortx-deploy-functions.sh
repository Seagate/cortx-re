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


HOST_FILE=$PWD/hosts
CORTX_SCRIPTS_REPO="https://github.com/Seagate/cortx-k8s/"
CORTX_SCRIPTS_BRANCH="UDX-5986_cortxProvisioner_cortxData_with_dummy_containers"
SYSTESM_DRIVE="/dev/sdg"

#On master
#download CORTX k8 deployment scripts

function download_deploy_script(){
        SCRIPT_LOCTION="/root/deploy-scripts"
        rm -rf $SCRIPT_LOCTION
	yum install git -y
	git clone https://$GITHUB_TOKEN@github.com/Seagate/cortx-k8s/ -b UDX-5986_cortxProvisioner_cortxData_with_dummy_containers $SCRIPT_LOCTION
}

#modify solution.yaml

function update_solution_config(){

pushd $SCRIPT_LOCTION/k8_cortx_cloud
echo > solution.yaml
count=0
for node in $(kubectl get node --selector='!node-role.kubernetes.io/master' | grep -v NAME | awk '{print $1}')
do
echo $node
echo $count
i=$node yq e -i '.solution.nodes['$count'].node'$count'.name = env(i)' solution.yaml
yq e -i '.solution.nodes['$count'].node'$count'.volumes.local = "/mnt/fs-local-volume"' solution.yaml
yq e -i '.solution.nodes['$count'].node'$count'.volumes.share = "/mnt/fs-local-volume"' solution.yaml
yq e -i '.solution.nodes['$count'].node'$count'.volumes.devices.system = "/dev/sda"' solution.yaml
yq e -i '.solution.nodes['$count'].node'$count'.volumes.devices.log = "/dev/sdb"' solution.yaml
yq e -i '.solution.nodes['$count'].node'$count'.volumes.devices.meta0 = "/dev/sdc"' solution.yaml
yq e -i '.solution.nodes['$count'].node'$count'.volumes.devices.metadata = "/dev/sdd"' solution.yaml
yq e -i '.solution.nodes['$count'].node'$count'.volumes.devices.data.d1 = "/dev/sde"' solution.yaml
yq e -i '.solution.nodes['$count'].node'$count'.volumes.devices.data.d2 = "/dev/sdf"' solution.yaml
count=$((count+1))
done

sed -i 's/- //g' solution.yaml
popd

}

#execute script

function execute_deploy_script(){

pushd $SCRIPT_LOCTION/k8_cortx_cloud
chmod +x *.sh
./deploy-cortx-cloud.sh
popd

}


#On worker
#format and mount system drive

function mount_system_device(){

mkfs.ext4 -F $SYSTESM_DRIVE
mkdir -p /mnt/fs-local-volume
mount -t ext4 $SYSTESM_DRIVE /mnt/fs-local-volume
}

#glusterfes requirements

function glusterfs_requirements(){

mkdir -p /mnt/fs-local-volume/etc/gluster
mkdir -p /mnt/fs-local-volume/var/log/gluster
mkdir -p /mnt/fs-local-volume/var/lib/glusterd

yum install glusterfs-fuse -y
}

#openldap requirements

function openldap_requiremenrs(){

mkdir -p /var/lib/ldap
grep -qxF "ldap:x:55:" /etc/group || echo "ldap:x:55:" >> /etc/group
grep -qxF "ldap:x:55:55:OpenLDAP server:/var/lib/ldap:/sbin/nologin" /etc/group || echo "ldap:x:55:55:OpenLDAP server:/var/lib/ldap:/sbin/nologin" >> /etc/group
chown -R ldap.ldap /var/lib/ldap

}

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
execute_deploy_script
}


function setup_worker_node(){
echo "---------------------------------------[ Setting up Worker Node ]--------------------------------------"
mount_system_device
glusterfs_requirements
openldap_requiremenrs
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

