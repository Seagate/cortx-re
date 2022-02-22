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

set -eo pipefail

SYSTEM_DRIVE="/dev/sdb"
SYSTEM_DRIVE_MOUNT="/mnt/fs-local-volume"
SCRIPT_LOCATION="/root/deploy-scripts"
YQ_VERSION=v4.13.3
YQ_BINARY=yq_linux_386
SOLUTION_CONFIG="/var/tmp/solution.yaml"

#On primary
#download CORTX k8 deployment scripts

function download_deploy_script(){
    if [ -z "$SCRIPT_LOCATION" ]; then echo "SCRIPT_LOCATION not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_SCRIPTS_REPO" ]; then echo "CORTX_SCRIPTS_REPO not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_SCRIPTS_BRANCH" ]; then echo "CORTX_SCRIPTS_BRANCH not provided.Exiting..."; exit 1; fi

    rm -rf $SCRIPT_LOCATION
    yum install git -y
    git clone https://github.com/$CORTX_SCRIPTS_REPO $SCRIPT_LOCATION
    pushd $SCRIPT_LOCATION
    git checkout $CORTX_SCRIPTS_BRANCH
    popd
}

#Install yq 4.13.3

function install_yq(){
    pip3 show yq && pip3 uninstall yq -y
    wget https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/${YQ_BINARY}.tar.gz -O - | tar xz && mv ${YQ_BINARY} /usr/bin/yq
    if [ -f /usr/local/bin/yq ]; then rm -rf /usr/local/bin/yq; fi    
    ln -s /usr/bin/yq /usr/local/bin/yq
}

#modify solution.yaml

function update_solution_config(){
    pushd $SCRIPT_LOCATION/k8_cortx_cloud
        echo > solution.yaml
        yq e -i '.solution.namespace = "default"' solution.yaml
        
        yq e -i '.solution.secrets.name = "cortx-secret"' solution.yaml
        yq e -i '.solution.secrets.content.openldap_admin_secret = "seagate1"' solution.yaml
        yq e -i '.solution.secrets.content.kafka_admin_secret = "Seagate@123"' solution.yaml
        yq e -i '.solution.secrets.content.consul_admin_secret = "Seagate@123"' solution.yaml
        yq e -i '.solution.secrets.content.common_admin_secret = "Seagate@123"' solution.yaml
        yq e -i '.solution.secrets.content.s3_auth_admin_secret = "ldapadmin"' solution.yaml
        yq e -i '.solution.secrets.content.csm_auth_admin_secret = "seagate2"' solution.yaml
        yq e -i '.solution.secrets.content.csm_mgmt_admin_secret = "Cortxadmin@123"' solution.yaml

        yq e -i '.solution.images.openldap = "ghcr.io/seagate/symas-openldap:2.4.58"' solution.yaml
        yq e -i '.solution.images.consul = "ghcr.io/seagate/consul:1.10.0"' solution.yaml
        yq e -i '.solution.images.kafka = "ghcr.io/seagate/kafka:3.0.0-debian-10-r7"' solution.yaml
        yq e -i '.solution.images.zookeeper = "ghcr.io/seagate/zookeeper:3.7.0-debian-10-r182"' solution.yaml
        yq e -i '.solution.images.rancher = "ghcr.io/seagate/local-path-provisioner:v0.0.20"' solution.yaml
        yq e -i '.solution.images.busybox = "ghcr.io/seagate/busybox:latest"' solution.yaml

        drive=$SYSTEM_DRIVE_MOUNT yq e -i '.solution.common.storage_provisioner_path = env(drive)' solution.yaml
        yq e -i '.solution.common.setup_size = "small"' solution.yaml
        yq e -i '.solution.common.container_path.local = "/etc/cortx"' solution.yaml
        yq e -i '.solution.common.container_path.shared = "/share"' solution.yaml
        yq e -i '.solution.common.container_path.log = "/etc/cortx/log"' solution.yaml
        yq e -i '.solution.common.s3.num_inst = 2' solution.yaml
        yq e -i '.solution.common.s3.start_port_num = 28051' solution.yaml
        yq e -i '.solution.common.s3.max_start_timeout = 240' solution.yaml
        yq e -i '.solution.common.motr.num_client_inst = 0' solution.yaml
        yq e -i '.solution.common.motr.start_port_num = 29000' solution.yaml
        yq e -i '.solution.common.hax.protocol = "https"' solution.yaml
        yq e -i '.solution.common.hax.service_name = "cortx-hax-svc"' solution.yaml
        yq e -i '.solution.common.hax.port_num = 22003' solution.yaml
        yq e -i '.solution.common.storage_sets.name = "storage-set-1"' solution.yaml

        sns=$SNS_CONFIG yq e -i '.solution.common.storage_sets.durability.sns = env(sns)' solution.yaml
        dix=$DIX_CONFIG yq e -i '.solution.common.storage_sets.durability.dix = env(dix)' solution.yaml
        yq e -i '.solution.common.external_services.type = "LoadBalancer"' solution.yaml

        yq e -i '.solution.common.resource_allocation.consul.server.storage = "10Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.server.resources.requests.memory = "100Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.server.resources.requests.cpu = "100m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.server.resources.limits.memory = "300Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.server.resources.limits.cpu = "100m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.client.resources.requests.memory = "100Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.client.resources.requests.cpu = "100m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.client.resources.limits.memory = "300Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.client.resources.limits.cpu = "100m"' solution.yaml

        yq e -i '.solution.common.resource_allocation.openldap.resources.requests.memory = "1Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.openldap.resources.requests.cpu = 2' solution.yaml
        yq e -i '.solution.common.resource_allocation.openldap.resources.limits.memory = "1Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.openldap.resources.limits.cpu = 2' solution.yaml

        yq e -i '.solution.common.resource_allocation.zookeeper.storage_request_size = "8Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.zookeeper.data_log_dir_request_size = "8Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.zookeeper.resources.requests.memory = "256Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.zookeeper.resources.requests.cpu = "250m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.zookeeper.resources.limits.memory = "512Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.zookeeper.resources.limits.cpu = "500m"' solution.yaml

        yq e -i '.solution.common.resource_allocation.kafka.storage_request_size = "8Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.kafka.log_persistence_request_size = "8Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.kafka.resources.requests.memory = "1Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.kafka.resources.requests.cpu = "250m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.kafka.resources.limits.memory = "2Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.kafka.resources.limits.cpu = 1' solution.yaml

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
    popd
}        


function add_image_info(){
echo "Updating cortx-all image info in solution.yaml"   
pushd $SCRIPT_LOCATION/k8_cortx_cloud
    image=$CORTX_IMAGE yq e -i '.solution.images.cortxcontrolprov = env(image)' solution.yaml	
    image=$CORTX_IMAGE yq e -i '.solution.images.cortxcontrol = env(image)' solution.yaml	
    image=$CORTX_IMAGE yq e -i '.solution.images.cortxdataprov = env(image)' solution.yaml
    image=$CORTX_IMAGE yq e -i '.solution.images.cortxdata = env(image)' solution.yaml
    image=$CORTX_IMAGE yq e -i '.solution.images.cortxserver = env(image)' solution.yaml
    image=$CORTX_IMAGE yq e -i '.solution.images.cortxha = env(image)' solution.yaml
    image=$CORTX_IMAGE yq e -i '.solution.images.cortxclient = env(image)' solution.yaml
popd 
}

function add_node_info_solution_config(){
echo "Updating node info in solution.yaml"    

    pushd $SCRIPT_LOCATION/k8_cortx_cloud
        count=1
        for node in $(kubectl get nodes -o jsonpath="{range .items[*]}{.metadata.name} {.spec.taints[?(@.effect=='NoSchedule')].effect}{\"\n\"}{end}" | grep -v NoSchedule)
            do
            i=$node yq e -i '.solution.nodes['$count'].node'$count'.name = env(i)' solution.yaml
            count=$((count+1))
        done
        sed -i -e 's/- //g' -e '/null/d' solution.yaml
    popd
}

copy_solution_config(){
	if [ -z "$SOLUTION_CONFIG" ]; then echo "SOLUTION_CONFIG not provided.Exiting..."; exit 1; fi
	echo "Copying $SOLUTION_CONFIG file" 
	pushd $SCRIPT_LOCATION/k8_cortx_cloud
            if [ -f '$SOLUTION_CONFIG' ]; then echo "file $SOLUTION_CONFIG not available..."; exit 1; fi	
            cp $SOLUTION_CONFIG .
            yq eval -i 'del(.solution.nodes)' solution.yaml
        popd 
}

#execute script

function execute_deploy_script(){
    local SCRIPT_NAME=$1
        pushd $SCRIPT_LOCATION/k8_cortx_cloud
        chmod +x *.sh
        ./$SCRIPT_NAME
        if [ $? -eq 0 ] 
        then 
           echo "Successfully executed $SCRIPT_NAME." 
        else 
           echo "Error in executing $SCRIPT_NAME. Please check cluster logs."
           exit 1
        fi
    popd
}

#On worker
#format and mount system drive

function mount_system_device(){
    findmnt $SYSTEM_DRIVE && umount -l $SYSTEM_DRIVE
    mkfs.ext4 -F $SYSTEM_DRIVE
    mkdir -p /mnt/fs-local-volume
    mount -t ext4 $SYSTEM_DRIVE $SYSTEM_DRIVE_MOUNT
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

function execute_prereq(){
    echo "Pulling latest CORTX-ALL image"
    docker pull $CORTX_IMAGE || echo "Failed to pull $CORTX_IMAGE"
    pushd $SCRIPT_LOCATION/k8_cortx_cloud
        findmnt $SYSTEM_DRIVE && umount -l $SYSTEM_DRIVE
        ./prereq-deploy-cortx-cloud.sh $SYSTEM_DRIVE
    popd    
}

function usage(){
    cat << HEREDOC
Usage : $0 [--setup-worker, --setup-primary, --third-party, --cortx-cluster, --destroy, --status]
where,
    --setup-worker - Setup k8 worker node for CORTX deployment
    --setup-primary - Setup k8 primary node for CORTX deployment
    --third-party - Deploy third-party components
    --cortx-cluster - Deploy Third-Party and CORTX components
    --destroy - Destroy CORTX Cluster
    --status - Print CLUSTER status
HEREDOC
}


ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

function setup_primary_node(){
echo "---------------------------------------[ Setting up Primary Node $HOSTNAME ]--------------------------------------"
    #Clean up untagged docker images and stopped docker containers.
    cleanup
    #Third-party images are downloaded from GitHub container registry. 
    download_deploy_script
    install_yq
    
    if [ "$(kubectl get  nodes $HOSTNAME  -o jsonpath="{range .items[*]}{.metadata.name} {.spec.taints}" | grep -o NoSchedule)" == "" ]; then
        execute_prereq
    fi

    if [ "$SOLUTION_CONFIG_TYPE" == "manual" ]; then
        copy_solution_config
    else
        update_solution_config
    fi
    
    add_image_info
    add_node_info_solution_config
}

function setup_worker_node(){
echo "---------------------------------------[ Setting up Worker Node on $HOSTNAME ]--------------------------------------"
    #Clean up untagged docker images and stopped docker containers.
    cleanup
    #Third-party images are downloaded from GitHub container registry.
    download_deploy_script
    execute_prereq
}

function destroy(){
   if [ "$(/usr/bin/kubectl get pods --no-headers | wc -l)" -gt 0 ]; then 
        pushd "$SCRIPT_LOCATION"/k8_cortx_cloud || echo "CORTX Deploy Scripts are not available on system"
            chmod +x *.sh
            ./destroy-cortx-cloud.sh
        popd || exit
        findmnt "$SYSTEM_DRIVE" && umount -l "$SYSTEM_DRIVE"
        files_to_remove=(
            "/mnt/fs-local-volume/"
            "/root/deploy-scripts/"
            "/root/get_helm.sh"
            "/root/calico*"
            "/root/.cache"
            "/root/.config"
            "/root/install.postnochroot.log"
            "/root/original-ks.cfg"
            "/etc/pip.conf"
        )
        for file in "${files_to_remove[@]}"; do
            if [ -f "$file" ] || [ -d "$file" ]; then
                echo "Removing file/folder $file"
                rm -rf "$file"
            fi
        done
    else 
        echo "CORTX Cluster is not already deployed"
    fi
}

function print_pod_status(){
echo "------------------------------------[ Image Details ]--------------------------------------"
      kubectl get pods -o jsonpath="{.items[*].spec.containers[*].image}" | tr ' ' '\n' | uniq 
echo "---------------------------------------[ POD Status ]--------------------------------------"
    if ! kubectl get pods | grep -v STATUS | awk '{ print $3}' |  grep -v -q -i running; then
      kubectl get pods -o wide
    else
echo "-----------[ All POD's are not in running state. Marking deployment as failed. Please check problematic pod events using kubectl describe pod <pod name> ]--------------------"
      exit 1
    fi
echo "-----------[ Sleeping for 1min before checking hctl status.... ]--------------------"
    sleep 60  
echo "---------------------------------------[ hctl status ]-----------------------------------------"
    SECONDS=0
    date
    while [[ SECONDS -lt 1200 ]] ; do
        if [ "$DEPLOYMENT_TYPE" == "provisioner" ]; then
            echo "Deployment type is: $DEPLOYMENT_TYPE"
            if kubectl exec -it $(kubectl get pods | awk '/server-node/{print $1; exit}') -c cortx-motr-hax -- hctl status > /dev/null ; then
                    if ! kubectl exec -it $(kubectl get pods | awk '/server-node/{print $1; exit}') -c cortx-motr-hax -- hctl status| grep -q -E 'unknown|offline|failed'; then
                        kubectl exec -it $(kubectl get pods | awk '/server-node/{print $1; exit}') -c cortx-motr-hax -- hctl status
                        echo "-----------[ Time taken for service to start $((SECONDS/60)) mins ]--------------------"
                        exit 0
                    else
                        echo "-----------[ Waiting for services to become online. Sleeping for 1min.... ]--------------------"
                        sleep 60
                    fi
            else
                echo "----------------------[ hctl status not working yet. Sleeping for 1min.... ]-------------------------"
                sleep 60
            fi
        else
            if kubectl exec -it $(kubectl get pods | awk '/cortx-server/{print $1; exit}') -c cortx-hax -- hctl status > /dev/null ; then
                    if ! kubectl exec -it $(kubectl get pods | awk '/cortx-server/{print $1; exit}') -c cortx-hax -- hctl status| grep -q -E 'unknown|offline|failed'; then
                        kubectl exec -it $(kubectl get pods | awk '/cortx-server/{print $1; exit}') -c cortx-hax -- hctl status
                        echo "-----------[ Time taken for service to start $((SECONDS/60)) mins ]--------------------"
                        exit 0
                    else
                        echo "-----------[ Waiting for services to become online. Sleeping for 1min.... ]--------------------"
                        sleep 60
                    fi
            else
                echo "----------------------[ hctl status not working yet. Sleeping for 1min.... ]-------------------------"
                sleep 60
            fi
        fi
    done
        echo "-----------[ Failed to to start services within 20mins. Exiting....]--------------------"
        exit 1
}

function io_exec(){
    CURRENT_OS=$(cut -d ' ' -f 1,4 < /etc/redhat-release)
    if [[ "$CURRENT_OS" == "Rocky 8.4" ]]; then
        echo "S3 IO Testing not enabled for Rocky Linux yet. Skipping"
    else
        pushd /var/tmp/
            chmod +x *.sh
            # "Setting up S3 client..."
            ./s3-client-setup.sh
            # "Running IO test..."
            ./io-testing.sh
        popd
    fi
}

function logs_generation(){
    echo -e "\n-----------[ Generating CORTX Support Bundle Logs... ]--------------------"
    pushd $SCRIPT_LOCATION/k8_cortx_cloud
        ./logs-cortx-cloud.sh
    popd
}

function cleanup(){
    echo -e "\n-----------[ Clean up untagged/unused images and stopped containers... ]--------------------"
    docker system prune -a -f --filter "label!=vendor=Project Calico"
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
    --io-test)
        io_exec
    ;;
    --generate-logs)
        logs_generation
    ;;
    --setup-primary)
        setup_primary_node 
    ;;
    *)
        echo "ERROR : Please provide valid option"
        usage
        exit 1
    ;;    
esac
