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

CORTX_SCRIPTS_REPO="Seagate/cortx-k8s/"
CORTX_SCRIPTS_BRANCH="UDX-5986_cortxProvisioner_cortxData_with_dummy_containers"
SYSTESM_DRIVE="/dev/sdg"
SCRIPT_LOCTION="/root/deploy-scripts"

#On master
#download CORTX k8 deployment scripts

function download_deploy_script(){
    rm -rf $SCRIPT_LOCTION
	yum install git -y
	git clone https://$GITHUB_TOKEN@github.com/$CORTX_SCRIPTS_REPO -b $CORTX_SCRIPTS_BRANCH $SCRIPT_LOCTION
}

#Install yq 4.13.3

function install_yq(){
    VERSION=v4.13.3
    BINARY=yq_linux_386
    wget https://github.com/mikefarah/yq/releases/download/${VERSION}/${BINARY}.tar.gz -O - | tar xz && mv ${BINARY} /usr/bin/yq
    ln -s /usr/bin/yq /usr/local/bin/yq
}

#modify solution.yaml

function update_solution_config(){
    pushd $SCRIPT_LOCTION/k8_cortx_cloud
        echo > solution.yaml
        count=0
        for node in $(kubectl get node --selector='!node-role.kubernetes.io/master' | grep -v NAME | awk '{print $1}')
            do
            i=$node yq e -i '.solution.nodes['$count'].node'$count'.name = env(i)' solution.yaml
            yq e -i '.solution.nodes['$count'].node'$count'.volumes.local = "/mnt/fs-local-volume"' solution.yaml
            yq e -i '.solution.nodes['$count'].node'$count'.volumes.share = "/mnt/fs-local-volume"' solution.yaml
            drive=$SYSTESM_DRIVE yq e -i '.solution.nodes['$count'].node'$count'.volumes.devices.system = env(drive)' solution.yaml
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
    local SCRIPT_NAME=$1
        pushd $SCRIPT_LOCTION/k8_cortx_cloud
        chmod +x *.sh
        ./$SCRIPT_NAME
    popd
}

#On worker
#format and mount system drive

function mount_system_device(){
    umount -l $SYSTESM_DRIVE
    mkfs.ext4 -F $SYSTESM_DRIVE
    mkdir -p /mnt/fs-local-volume
    mount -t ext4 $SYSTESM_DRIVE /mnt/fs-local-volume
    mkdir -p /mnt/fs-local-volume/local-path-provisioner
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
    grep -qxF "ldap:x:55:55:OpenLDAP server:/var/lib/ldap:/sbin/nologin" /etc/passwd || echo "ldap:x:55:55:OpenLDAP server:/var/lib/ldap:/sbin/nologin" >> /etc/passwd
    chown -R ldap.ldap /var/lib/ldap
}

function download_images(){
    mkdir -p /var/images && pushd /var/images
        wget -r -np -nH --cut-dirs=3 -A *.tar http://cortx-storage.colo.seagate.com/releases/cortx/images/
        for file in $(ls -1); do docker load -i $file; done
    popd 
}

function usage(){
    cat << HEREDOC
Usage : $0 [--setup-worker, --setup-master, --third-party, --cortx-cluster, --destroy, --status]
where,
    --setup-worker - Setup k8 worker node for CORTX deployment
    --setup-master - Setup k8 master node for CORTX deployment
    --third-party - Deploy third-party components
    --cortx-cluster - Deploy Third-Party and CORTX components
    --destroy - Destroy CORTX Cluster
    --status - Print CLUSTER status
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
    install_yq
    update_solution_config
}


function setup_worker_node(){
echo "---------------------------------------[ Setting up Worker Node ]--------------------------------------"
    mount_system_device
    download_images
    glusterfs_requirements
    openldap_requiremenrs
}

function destroy(){
    pushd $SCRIPT_LOCTION/k8_cortx_cloud
        chmod +x *.sh
        ./destroy-cortx-cloud.sh
    popd
}

function print_pod_status(){
     rm -rf /var/tmp/cortx-cluster-status.txt
     kubectl get pods -o wide
}

case $ACTION in
    --third-party)
        execute_deploy_script deploy-cortx-cloud-3rd-party.sh
    ;;
    --cortx-cluster)
        execute_deploy_script deploy-cortx-cloud.sh
    ;;
    --destroy)
        destroy
    ;;
    --setup-worker) 
        setup_worker_node
    ;;
    --status) 
        print_pod_status
    ;;
    --setup-master)
        setup_master_node
    ;;
    *)
        echo "ERROR : Please provide valid option"
        usage
        exit 1
    ;;    
esac
