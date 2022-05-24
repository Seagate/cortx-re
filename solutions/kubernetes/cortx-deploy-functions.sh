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

source /var/tmp/functions.sh

SYSTEM_DRIVE_MOUNT="/mnt/fs-local-volume"
SCRIPT_LOCATION="/root/deploy-scripts"
YQ_VERSION=v4.25.1
YQ_BINARY=yq_linux_386
SOLUTION_CONFIG="/var/tmp/solution.yaml"

function usage() {
    cat << HEREDOC
Usage : $0 [--cortx-cluster, --setup-primary, --setup-worker, --status, --io-sanity, --destroy, --generate-logs]
where,
    --cortx-cluster - Deploy Third-Party and CORTX components.
    --setup-primary - Setup k8 primary node for CORTX deployment.
    --setup-worker - Setup k8 worker node for CORTX deployment.
    --status - Print CORTX cluster status.
    --io-sanity - Perform IO sanity test.
    --destroy - Destroy CORTX Cluster.
    --generate-logs - Generate support bundle logs. 
HEREDOC
}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

# On primary
# Download CORTX k8 deployment scripts

function download_deploy_script() {
    if [ -z "$SCRIPT_LOCATION" ]; then echo "SCRIPT_LOCATION not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_SCRIPTS_REPO" ]; then echo "CORTX_SCRIPTS_REPO not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_SCRIPTS_BRANCH" ]; then echo "CORTX_SCRIPTS_BRANCH not provided.Exiting..."; exit 1; fi

    rm -rf $SCRIPT_LOCATION
    yum install git -y
    git clone https://github.com/$CORTX_SCRIPTS_REPO $SCRIPT_LOCATION
    pushd $SCRIPT_LOCATION
    git checkout $CORTX_SCRIPTS_BRANCH
    echo "DEBUG:Dummy solution.yaml"
    cp k8_cortx_cloud/solution.example.yaml k8_cortx_cloud/solution.yaml
    popd
}

# Install yq 4.13.3

function install_yq() {
    add_secondary_separator "Installing yq-$YQ_VERSION"
    pip3 show yq && pip3 uninstall yq -y
    wget https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/${YQ_BINARY}.tar.gz -O - | tar xz && mv ${YQ_BINARY} /usr/bin/yq
    if [ -f /usr/local/bin/yq ]; then rm -rf /usr/local/bin/yq; fi    
    ln -s /usr/bin/yq /usr/local/bin/yq
}

# Modify solution.yaml

function update_solution_config(){
    pushd $SCRIPT_LOCATION/k8_cortx_cloud
        echo > solution.yaml
        NAMESPACE=$NAMESPACE yq e -i '.solution.namespace = env(NAMESPACE)' solution.yaml
        deployment_method=$DEPLOYMENT_METHOD yq e -i '.solution.deployment_type = env(deployment_method)' solution.yaml
        
        yq e -i '.solution.secrets.name = "cortx-secret"' solution.yaml
        yq e -i '.solution.secrets.content.kafka_admin_secret = "null"' solution.yaml
        yq e -i '.solution.secrets.content.consul_admin_secret = "null"' solution.yaml
        yq e -i '.solution.secrets.content.common_admin_secret = "null"' solution.yaml
        yq e -i '.solution.secrets.content.s3_auth_admin_secret = "ldapadmin"' solution.yaml
        yq e -i '.solution.secrets.content.csm_auth_admin_secret = "null"' solution.yaml
        yq e -i '.solution.secrets.content.csm_mgmt_admin_secret = "Cortxadmin@123"' solution.yaml

        yq e -i '.solution.images.consul = "ghcr.io/seagate/consul:1.11.4"' solution.yaml
        yq e -i '.solution.images.kafka = "ghcr.io/seagate/kafka:3.0.0-debian-10-r97"' solution.yaml
        yq e -i '.solution.images.zookeeper = "ghcr.io/seagate/zookeeper:3.8.0-debian-10-r9"' solution.yaml
        yq e -i '.solution.images.rancher = "ghcr.io/seagate/local-path-provisioner:v0.0.20"' solution.yaml
        yq e -i '.solution.images.busybox = "ghcr.io/seagate/busybox:latest"' solution.yaml

        drive=$SYSTEM_DRIVE_MOUNT yq e -i '.solution.common.storage_provisioner_path = env(drive)' solution.yaml
        yq e -i '.solution.common.setup_size = "small"' solution.yaml
        yq e -i '.solution.common.container_path.local = "/etc/cortx"' solution.yaml
        yq e -i '.solution.common.container_path.log = "/etc/cortx/log"' solution.yaml
        yq e -i '.solution.common.s3.default_iam_users.auth_admin = "sgiamadmin"' solution.yaml
        yq e -i '.solution.common.s3.default_iam_users.auth_user = "user_name"' solution.yaml
        yq e -i '.solution.common.s3.max_start_timeout = 240' solution.yaml
        yq e -i '.solution.common.s3.extra_configuration = ""' solution.yaml
        if [ "$DEPLOYMENT_METHOD" == "data-only" ]; then
            yq e -i '.solution.common.motr.num_client_inst = 1' solution.yaml
        else
            yq e -i '.solution.common.motr.num_client_inst = 0' solution.yaml
        fi       
        yq e -i '.solution.common.motr.start_port_num = 29000' solution.yaml
        yq e -i '.solution.common.motr.extra_configuration = ""' solution.yaml
        yq e -i '.solution.common.hax.protocol = "https"' solution.yaml
        yq e -i '.solution.common.hax.service_name = "cortx-hax-svc"' solution.yaml
        yq e -i '.solution.common.hax.port_num = 22003' solution.yaml
        yq e -i '.solution.common.storage_sets.name = "storage-set-1"' solution.yaml

        sns=$SNS_CONFIG yq e -i '.solution.common.storage_sets.durability.sns = env(sns)' solution.yaml
        dix=$DIX_CONFIG yq e -i '.solution.common.storage_sets.durability.dix = env(dix)' solution.yaml
        
        external_exposure_service=$EXTERNAL_EXPOSURE_SERVICE yq e -i '.solution.common.external_services.s3.type = env(external_exposure_service)' solution.yaml
        yq e -i '.solution.common.external_services.s3.count = 1' solution.yaml
        yq e -i '.solution.common.external_services.s3.ports.http = "80"' solution.yaml
        yq e -i '.solution.common.external_services.s3.ports.https = "443"' solution.yaml
        s3_external_http_nodeport=$S3_EXTERNAL_HTTP_NODEPORT yq e -i '.solution.common.external_services.s3.nodePorts.http = env(s3_external_http_nodeport)' solution.yaml
        s3_external_https_nodeport=$S3_EXTERNAL_HTTPS_NODEPORT yq e -i '.solution.common.external_services.s3.nodePorts.https = env(s3_external_https_nodeport)' solution.yaml
        
        external_exposure_service=$EXTERNAL_EXPOSURE_SERVICE yq e -i '.solution.common.external_services.control.type = env(external_exposure_service)' solution.yaml    
        yq e -i '.solution.common.external_services.control.ports.https = "8081"' solution.yaml    
        control_external_nodeport=$CONTROL_EXTERNAL_NODEPORT yq e -i '.solution.common.external_services.control.nodePorts.https = env(control_external_nodeport)' solution.yaml

        yq e -i '.solution.common.resource_allocation.consul.server.storage = "10Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.server.resources.requests.memory = "200Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.server.resources.requests.cpu = "200m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.server.resources.limits.memory = "500Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.server.resources.limits.cpu = "500m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.client.resources.requests.memory = "200Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.client.resources.requests.cpu = "200m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.client.resources.limits.memory = "500Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.consul.client.resources.limits.cpu = "500m"' solution.yaml

        yq e -i '.solution.common.resource_allocation.zookeeper.storage_request_size = "8Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.zookeeper.data_log_dir_request_size = "8Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.zookeeper.resources.requests.memory = "256Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.zookeeper.resources.requests.cpu = "250m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.zookeeper.resources.limits.memory = "1Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.zookeeper.resources.limits.cpu = "1000m"' solution.yaml

        yq e -i '.solution.common.resource_allocation.kafka.storage_request_size = "8Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.kafka.log_persistence_request_size = "8Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.kafka.resources.requests.memory = "1Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.kafka.resources.requests.cpu = "250m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.kafka.resources.limits.memory = "3Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.kafka.resources.limits.cpu = "1000m"' solution.yaml

        yq e -i '.solution.common.resource_allocation.hare.hax.resources.requests.memory = "128Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.hare.hax.resources.requests.cpu = "250m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.hare.hax.resources.limits.memory = "2Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.hare.hax.resources.limits.cpu = "1000m"' solution.yaml

        yq e -i '.solution.common.resource_allocation.data.motr.resources.requests.memory = "1Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.data.motr.resources.requests.cpu = "250m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.data.motr.resources.limits.memory = "2Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.data.motr.resources.limits.cpu = "1000m"' solution.yaml

        yq e -i '.solution.common.resource_allocation.data.confd.resources.requests.memory = "128Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.data.confd.resources.requests.cpu = "250m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.data.confd.resources.limits.memory = "512Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.data.confd.resources.limits.cpu = "500m"' solution.yaml

        yq e -i '.solution.common.resource_allocation.server.rgw.resources.requests.memory = "128Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.server.rgw.resources.requests.cpu = "250m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.server.rgw.resources.limits.memory = "2Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.server.rgw.resources.limits.cpu = "2000m"' solution.yaml

        yq e -i '.solution.common.resource_allocation.control.agent.resources.requests.memory = "128Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.control.agent.resources.requests.cpu = "250m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.control.agent.resources.limits.memory = "256Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.control.agent.resources.limits.cpu = "500m"' solution.yaml

        yq e -i '.solution.common.resource_allocation.ha.fault_tolerance.resources.requests.memory = "128Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.ha.fault_tolerance.resources.requests.cpu = "250m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.ha.fault_tolerance.resources.limits.memory = "1Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.ha.fault_tolerance.resources.limits.cpu = "500m"' solution.yaml

        yq e -i '.solution.common.resource_allocation.ha.health_monitor.resources.requests.memory = "128Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.ha.health_monitor.resources.requests.cpu = "250m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.ha.health_monitor.resources.limits.memory = "1Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.ha.health_monitor.resources.limits.cpu = "500m"' solution.yaml

        yq e -i '.solution.common.resource_allocation.ha.k8s_monitor.resources.requests.memory = "128Mi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.ha.k8s_monitor.resources.requests.cpu = "250m"' solution.yaml
        yq e -i '.solution.common.resource_allocation.ha.k8s_monitor.resources.limits.memory = "1Gi"' solution.yaml
        yq e -i '.solution.common.resource_allocation.ha.k8s_monitor.resources.limits.cpu = "500m"' solution.yaml

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

function add_image_info() {
echo "Updating cortx images info in solution.yaml"   
pushd $SCRIPT_LOCATION/k8_cortx_cloud
    image=$CORTX_CONTROL_IMAGE yq e -i '.solution.images.cortxcontrol = env(image)' solution.yaml	
    image=$CORTX_DATA_IMAGE yq e -i '.solution.images.cortxdata = env(image)' solution.yaml
    image=$CORTX_SERVER_IMAGE yq e -i '.solution.images.cortxserver = env(image)' solution.yaml
    image=$CORTX_CONTROL_IMAGE yq e -i '.solution.images.cortxha = env(image)' solution.yaml
    image=$CORTX_DATA_IMAGE yq e -i '.solution.images.cortxclient = env(image)' solution.yaml
popd 
}

function add_node_info_solution_config() {
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

copy_solution_config() {
	if [ -z "$SOLUTION_CONFIG" ]; then echo "SOLUTION_CONFIG not provided.Exiting..."; exit 1; fi
	echo "Copying $SOLUTION_CONFIG file" 
	pushd $SCRIPT_LOCATION/k8_cortx_cloud
            if [ -f '$SOLUTION_CONFIG' ]; then echo "file $SOLUTION_CONFIG not available..."; exit 1; fi	
            cp $SOLUTION_CONFIG .
            yq eval -i 'del(.solution.nodes)' solution.yaml
            NAMESPACE=$(yq e '.solution.namespace' solution.yaml)
        popd 
}

setup_kubectl_context() {
    add_secondary_separator "Updated kubectl context to use $NAMESPACE"
    kubectl config set-context --current --namespace=$NAMESPACE
}

#execute script

function execute_deploy_script() {
    local SCRIPT_NAME=$1
        pushd $SCRIPT_LOCATION/k8_cortx_cloud
        chmod +x *.sh
        ./$SCRIPT_NAME
        if [ $? -eq 0 ] 
        then 
           echo -e "\nSuccessfully executed $SCRIPT_NAME." 
        else 
           echo -e "\nError in executing $SCRIPT_NAME. Please check cluster logs."
           exit 1
        fi
    popd
}

function execute_prereq() {
    add_secondary_separator "Pulling latest CORTX images"
    docker pull $CORTX_SERVER_IMAGE || { echo "Failed to pull $CORTX_SERVER_IMAGE"; exit 1; }
    docker pull $CORTX_DATA_IMAGE || { echo "Failed to pull $CORTX_DATA_IMAGE"; exit 1; }
    docker pull $CORTX_CONTROL_IMAGE || { echo "Failed to pull $CORTX_CONTROL_IMAGE"; exit 1; }
    pushd $SCRIPT_LOCATION/k8_cortx_cloud
        add_secondary_separator "Un-mounting $SYSTEM_DRIVE partition if already mounted"
        findmnt $SYSTEM_DRIVE && umount -l $SYSTEM_DRIVE
        add_secondary_separator "Executing ./prereq-deploy-cortx-cloud.sh"
        ./prereq-deploy-cortx-cloud.sh -d $SYSTEM_DRIVE
    popd    
}

function setup_primary_node() {
    #Clean up untagged docker images and stopped docker containers.
    cleanup
    #Third-party images are downloaded from GitHub container registry. 
    download_deploy_script
    install_yq

    if [ "$SOLUTION_CONFIG_TYPE" == "manual" ]; then
        copy_solution_config
    else
        update_solution_config
    fi

    if [ "$(kubectl get  nodes $HOSTNAME  -o jsonpath="{range .items[*]}{.metadata.name} {.spec.taints}" | grep -o NoSchedule)" == "" ]; then
        execute_prereq
    fi
    
    add_image_info
    add_node_info_solution_config
    setup_kubectl_context
}

function setup_worker_node() {
    add_secondary_separator "Setting up Worker Node on $HOSTNAME"
    #Clean up untagged docker images and stopped docker containers.
    cleanup
    #Third-party images are downloaded from GitHub container registry.
    download_deploy_script
    install_yq
    execute_prereq
}

function destroy() {
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

function print_pod_status() {
    add_secondary_separator "Image Details"
        kubectl get pods -o jsonpath="{.items[*].spec.containers[*].image}" | tr ' ' '\n' | uniq 
    add_secondary_separator "POD Status"
        if ! kubectl get pods | grep -v STATUS | awk '{ print $3}' |  grep -v -q -i running; then
        kubectl get pods -o wide
        else
    add_common_separator "All PODs are not in running state. Marking deployment as failed. Please check problematic pod events using kubectl describe pod <pod name>"
        exit 1
        fi
    add_common_separator "Sleeping for 1min before checking hctl status...."
        sleep 60  
    add_common_separator "hctl status"
    #    echo "Disabled htcl status check for now. Checking RGW service"
    #    kubectl exec -it $(kubectl get pods | awk '/cortx-server/{print $1; exit}') -c cortx-rgw -- ps -elf | grep rgw
        SECONDS=0
        date
        while [[ SECONDS -lt 1200 ]] ; do
            if [ "$DEPLOYMENT_METHOD" == "data-only" ]; then
                if kubectl exec -it $(kubectl get pods | awk '/cortx-data/{print $1; exit}') -c cortx-hax -- hctl status > /dev/null ; then
                    if ! kubectl exec -it $(kubectl get pods | awk '/cortx-data/{print $1; exit}') -c cortx-hax -- hctl status| grep -v motr_client | grep -q -E 'unknown|offline|failed'; then
                        kubectl exec -it $(kubectl get pods | awk '/cortx-data/{print $1; exit}') -c cortx-hax -- hctl status
                        add_secondary_separator "Time taken for service to start $((SECONDS/60)) mins"
                        exit 0
                    else
                        add_common_separator "Waiting for services to become online. Sleeping for 1min...."
                        sleep 60
                    fi
                else
                    add_common_separator "hctl status not working yet. Sleeping for 1min...."
                    sleep 60
                fi
            else
                if kubectl exec -it $(kubectl get pods | awk '/cortx-server/{print $1; exit}') -c cortx-hax -- hctl status > /dev/null ; then
                    if ! kubectl exec -it $(kubectl get pods | awk '/cortx-server/{print $1; exit}') -c cortx-hax -- hctl status| grep -q -E 'unknown|offline|failed'; then
                        kubectl exec -it $(kubectl get pods | awk '/cortx-server/{print $1; exit}') -c cortx-hax -- hctl status
                        add_secondary_separator "Time taken for service to start $((SECONDS/60)) mins"
                        exit 0
                    else
                        add_common_separator "Waiting for services to become online. Sleeping for 1min...."
                        sleep 60
                    fi
                else
                    add_common_separator "hctl status not working yet. Sleeping for 1min...."
                    sleep 60
                fi
            fi    
        done
            add_secondary_separator "Failed to to start services within 20mins. Exiting...."
            exit 1
}

function io_exec() {
    pushd /var/tmp/
        export DEPLOYMENT_METHOD=$DEPLOYMENT_METHOD
        ./io-sanity.sh
    popd
}

function logs_generation() {
    add_secondary_separator "Generating CORTX Support Bundle Logs..."
    pushd $SCRIPT_LOCATION/k8_cortx_cloud
        ./logs-cortx-cloud.sh
    popd
}

function cleanup() {
    add_secondary_separator "Clean up untagged/unused images and stopped containers..."
    docker system prune -a -f --filter "label!=vendor=Project Calico"
}

case $ACTION in
    --cortx-cluster)
        execute_deploy_script deploy-cortx-cloud.sh
    ;;
    --setup-primary)
        setup_primary_node 
    ;;
    --setup-worker) 
        setup_worker_node
    ;;
    --status) 
        print_pod_status
    ;;
    --io-sanity)
        io_exec
    ;;
    --destroy)
        destroy
    ;;
    --generate-logs)
        logs_generation
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;    
esac