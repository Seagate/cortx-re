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

SYSTESM_DRIVE="/dev/sdb"
SYSTEM_DRIVE_MOUNT="/mnt/fs-local-volume/"
SCRIPT_LOCATION="/root/deploy-scripts"
YQ_VERSION=v4.13.3
YQ_BINARY=yq_linux_386

#On master
#download CORTX k8 deployment scripts

function download_deploy_script(){
    if [ -z "$SCRIPT_LOCATION" ]; then echo "SCRIPT_LOCATION not provided.Exiting..."; exit 1; fi
    if [ -z "$GITHUB_TOKEN" ]; then echo "GITHUB_TOKEN not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_SCRIPTS_REPO" ]; then echo "CORTX_SCRIPTS_REPO not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_SCRIPTS_BRANCH" ]; then echo "CORTX_SCRIPTS_BRANCH not provided.Exiting..."; exit 1; fi

    rm -rf $SCRIPT_LOCATION
    yum install git -y
    git clone https://$GITHUB_TOKEN@github.com/$CORTX_SCRIPTS_REPO $SCRIPT_LOCATION
    pushd $SCRIPT_LOCATION
    git checkout $CORTX_SCRIPTS_BRANCH
    popd
}

#Install yq 4.13.3

function install_yq(){
    pip3 uninstall yq -y
    wget https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/${YQ_BINARY}.tar.gz -O - | tar xz && mv ${YQ_BINARY} /usr/bin/yq
    ln -s /usr/bin/yq /usr/local/bin/yq
}

#modify solution.yaml

function update_solution_config(){
    pushd $SCRIPT_LOCATION/k8_cortx_cloud
        echo > solution.yaml
        yq e -i '.solution.namespace = "default"' solution.yaml
        
        yq e -i '.solution.secrets.name = "cortx-secret"' solution.yaml
        yq e -i '.solution.secrets.content.openldap_admin_secret = "seagate2"' solution.yaml
        yq e -i '.solution.secrets.content.kafka_admin_secret = "Seagate@123"' solution.yaml
        yq e -i '.solution.secrets.content.consul_admin_secret = "Seagate@123"' solution.yaml
        yq e -i '.solution.secrets.content.common_admin_secret = "Seagate@123"' solution.yaml
        yq e -i '.solution.secrets.content.s3_auth_admin_secret = "ldapadmin"' solution.yaml
        yq e -i '.solution.secrets.content.csm_auth_admin_secret = "seagate2"' solution.yaml
        yq e -i '.solution.secrets.content.csm_mgmt_admin_secret = "Cortxadmin@123"' solution.yaml


        image=$CORTX_IMAGE yq e -i '.solution.images.cortxcontrolprov = env(image)' solution.yaml	
        image=$CORTX_IMAGE yq e -i '.solution.images.cortxcontrol = env(image)' solution.yaml	
        image=$CORTX_IMAGE yq e -i '.solution.images.cortxdataprov = env(image)' solution.yaml	
        image=$CORTX_IMAGE yq e -i '.solution.images.cortxdata = env(image)' solution.yaml

        yq e -i '.solution.images.openldap = "ghcr.io/seagate/symas-openldap:standalone"' solution.yaml
        yq e -i '.solution.images.consul = "hashicorp/consul:1.10.0"' solution.yaml
        yq e -i '.solution.images.kafka = "bitnami/kafka"' solution.yaml
        yq e -i '.solution.images.zookeeper = "bitnami/zookeeper"' solution.yaml
        yq e -i '.solution.images.gluster = "docker.io/gluster/gluster-centos"' solution.yaml
        yq e -i '.solution.images.rancher = "rancher/local-path-provisioner:v0.0.20"' solution.yaml
	
        yq e -i '.solution.3rdparty.openldap.password = "seagate2"' solution.yaml

        yq e -i '.solution.common.cortx_io_svc_ingress = false' solution.yaml
        yq e -i '.solution.common.storage.local = "/etc/cortx"' solution.yaml
        yq e -i '.solution.common.storage.shared = "/share"' solution.yaml
        yq e -i '.solution.common.storage.log = "/share/var/log/cortx"' solution.yaml
        yq e -i '.solution.common.s3.num_inst = 2' solution.yaml
        yq e -i '.solution.common.s3.start_port_num = 28051' solution.yaml
        yq e -i '.solution.common.motr.num_client_inst = 0' solution.yaml
        yq e -i '.solution.common.motr.start_port_num = 29000' solution.yaml
        yq e -i '.solution.common.storage_sets.name = "storage-set-1"' solution.yaml
        yq e -i '.solution.common.storage_sets.durability.sns = "1+0+0"' solution.yaml
        yq e -i '.solution.common.storage_sets.durability.dix = "1+0+0"' solution.yaml

        yq e -i '.solution.storage.cvg1.name = "cvg-01"' solution.yaml
        yq e -i '.solution.storage.cvg1.type = "ios"' solution.yaml
        yq e -i '.solution.storage.cvg1.devices.metadata.device = "/dev/sdc"' solution.yaml
        yq e -i '.solution.storage.cvg1.devices.metadata.size = "5Gi"' solution.yaml
        yq e -i '.solution.storage.cvg1.devices.data.d1.device = "/dev/sdd"' solution.yaml
        yq e -i '.solution.storage.cvg1.devices.data.d1.size = "5Gi"' solution.yaml
        yq e -i '.solution.storage.cvg1.devices.data.d2.device = "/dev/sde"' solution.yaml
        yq e -i '.solution.storage.cvg1.devices.data.d2.size = "5Gi"' solution.yaml
        
        yq e -i '.solution.storage.cvg2.name = "cvg-02"' solution.yaml
        yq e -i '.solution.storage.cvg2.type = "ios"' solution.yaml
        yq e -i '.solution.storage.cvg2.devices.metadata.device = "/dev/sdf"' solution.yaml
        yq e -i '.solution.storage.cvg2.devices.metadata.size = "5Gi"' solution.yaml
        yq e -i '.solution.storage.cvg2.devices.data.d1.device = "/dev/sdg"' solution.yaml
        yq e -i '.solution.storage.cvg2.devices.data.d1.size = "5Gi"' solution.yaml
        yq e -i '.solution.storage.cvg2.devices.data.d2.device = "/dev/sdh"' solution.yaml
        yq e -i '.solution.storage.cvg2.devices.data.d2.size = "5Gi"' solution.yaml
        
        count=0
        for node in $(kubectl get node --selector='!node-role.kubernetes.io/master' | grep -v NAME | awk '{print $1}')
            do
            i=$node yq e -i '.solution.nodes['$count'].node'$count'.name = env(i)' solution.yaml
            drive=$SYSTEM_DRIVE_MOUNT yq e -i '.solution.nodes['$count'].node'$count'.devices.system = env(drive)' solution.yaml
            count=$((count+1))
        done
        sed -i 's/- //g' solution.yaml
    popd
}

#execute script

function execute_deploy_script(){
    local SCRIPT_NAME=$1
        pushd $SCRIPT_LOCATION/k8_cortx_cloud
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
    mount -t ext4 $SYSTESM_DRIVE $SYSTEM_DRIVE_MOUNT
    mkdir -p /mnt/fs-local-volume/local-path-provisioner
    sysctl -w vm.max_map_count=30000000
}

#glusterfes requirements

function glusterfs_requirements(){
    mkdir -p /mnt/fs-local-volume/etc/gluster
    mkdir -p /mnt/fs-local-volume/var/log/glusterfs
    mkdir -p /mnt/fs-local-volume/var/lib/glusterd
    yum install glusterfs-fuse -y
}

#openldap requirements

function openldap_requiremenrs(){
    mkdir -p /etc/3rd-party/openldap
    mkdir -p /var/data/3rd-party
    mkdir -p /var/log/3rd-party
}

function download_images(){
    rm -rf /var/images
    mkdir -p /var/images && pushd /var/images
        wget -q -r -np -nH --cut-dirs=3 -A *.tar http://cortx-storage.colo.seagate.com/releases/cortx/images/
        for file in $(ls -1); do docker load -i $file; done
    popd
    
}

function execute_prereq(){
    pushd $SCRIPT_LOCATION/k8_cortx_cloud
        umount -l $SYSTESM_DRIVE
        ./prereq-deploy-cortx-cloud.sh $SYSTESM_DRIVE
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
echo "---------------------------------------[ Setting up Master Node $HOSTNAME ]--------------------------------------"
    download_deploy_script $GITHUB_TOKEN
    download_images
    install_yq
    update_solution_config
}


function setup_worker_node(){
echo "---------------------------------------[ Setting up Worker Node on $HOSTNAME ]--------------------------------------"
    download_images
    download_deploy_script
    execute_prereq
}

function destroy(){
    download_deploy_script $GITHUB_TOKEN
    pushd $SCRIPT_LOCATION/k8_cortx_cloud
        chmod +x *.sh
        ./destroy-cortx-cloud.sh
    popd
}

function print_pod_status(){
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
