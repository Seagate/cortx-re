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

CALICO_PLUGIN_VERSION=latest
K8_VERSION=1.19.0-0
DOCKER_VERSION=latest
OS_VERSION="CentOS 7.9.2009"
export Exception=100
export ConfigException=101


function usage(){
    cat << HEREDOC
Usage : $0 [--prepare, --master]
where,
    --prepare - Install prerequisites on nodes for kubernetes setup
    --master - Initialize K8 master node. 
HEREDOC
}

# try-catch functions
function try()
{
    [[ $- = *e* ]]; SAVED_OPT_E=$?
    set +e
}

function throw()
{
    exit $1
}

function catch()
{
    export ex_code=$?
    (( $SAVED_OPT_E )) && set +e
    return $ex_code
} 

function throwErrors()
{
    set -e
}

function ignoreErrors()
{
    set +e
}

function verify_os() {
    CURRENT_OS=$(cut -d ' ' -f 1,4 < /etc/redhat-release)
    if [ "$CURRENT_OS" != "$OS_VERSION" ]; then
        echo "ERROR : Operating System is not correct. Current OS : $CURRENT_OS, Required OS : $OS_VERSION"
        exit 1
    fi 
}

function print_cluster_status(){

    while kubectl get nodes | grep -v STATUS | awk '{print $2}' | tr '\n' ' ' | grep -q NotReady
    do
		sleep 5
    done
    kubectl get nodes -o wide
}

function cleanup_node(){

    # Cleanup kubeadm stuff
    if [ -f /usr/bin/kubeadm ]; then
        echo "Cleaning up existing kubeadm configuration"
        # unmount /var/lib/kubelet is having problem while running `kubeadm reset` in k8s v1.19. It is fixed in 1.20
        # Ref link - https://kubernetes.io/docs/setup/production-environment/tools/kubeadm/troubleshooting-kubeadm/#kubeadm-reset-unmounts-var-lib-kubelet
        kubeadm reset -f
    fi

    pkgs_to_remove=(
        "docker-ce"
        "docker-ce-cli"
        "containerd"
        "kubernetes-cni"
        "kubeadm"
        "kubectl"
    )
    files_to_remove=(
        "/etc/docker/daemon.json"
        "$HOME/.kube"
        "/etc/systemd/system/docker.service"
        "/etc/cni/net.d"
        "/etc/kubernetes"
        "/var/lib/kubelet"
        "/var/lib/etcd"
    )
    services_to_stop=(
        kubelet
        docker
    )
    # Set directive to remove packages with dependencies.
    searchString="clean_requirements_on_remove*"
    conffile="/etc/yum.conf"
    if grep -q "$searchString" "$conffile"
    then
        sed -i "/$searchString/d" "$conffile"
    fi
    echo "clean_requirements_on_remove=1" >> "$conffile"

    #Stopping Services.
    for service_name in ${services_to_stop[@]}; do
        if [ "$(systemctl list-unit-files | grep $service_name.service -c)" != "0" ]; then
            echo "Stopping $service_name"
            systemctl stop $service_name.service
        fi
    done


    # Remove packages
    echo "Uninstalling packages"
    yum clean all && rm -rf /var/cache/yum
    for pkg in ${pkgs_to_remove[@]}; do
        if rpm -qa "$pkg"; then
            yum remove "$pkg" -y
            if [ $? -ne 0 ]; then
                echo "Failed to uninstall $pkg"
                exit 1
            fi
        else
            echo -e "\t$pkg is not installed"
        fi
    done
    for file in ${files_to_remove[@]}; do
        if [ -f "$file" ] || [ -d "$file" ]; then
            echo "Removing file/folder $file"
            rm -rf $file
        fi
    done
}

function install_prerequisites(){
    try
    (   # disable swap
        verify_os 
        sudo swapoff -a
        # keeps the swaf off during reboot
        sudo sed -i '/ swap / s/^\(.*\)$/#\1/g' /etc/fstab

        # disable selinux
        setenforce 0
        sed -i  -e 's/SELINUX=enforcing/SELINUX=disabled/g' -e 's/SELINUX=enforcing/SELINUX=permissive/g' /etc/sysconfig/selinux || throw $Exception
    
        # stop and disable firewalld
        (systemctl stop firewalld && systemctl disable firewalld && sudo systemctl mask --now firewalld) || throw $Exception
        # install python packages
        (yum install python3-pip -y && pip3 install jq yq) || throw $Exception

        # install yum-utils and wget
        yum install yum-utils wget -y || throw $Exception

        # set yum repositories for k8 and docker-ce
        rm -rf /etc/yum.repos.d/download.docker.com_linux_centos_7_x86_64_stable_.repo /etc/yum.repos.d/packages.cloud.google.com_yum_repos_kubernetes-el7-x86_64.repo
        yum-config-manager --add https://packages.cloud.google.com/yum/repos/kubernetes-el7-x86_64 || throw $ConfigException
        yum-config-manager --add https://download.docker.com/linux/centos/7/x86_64/stable/ || throw $ConfigException     
        yum install kubeadm-$K8_VERSION kubectl-$K8_VERSION kubelet-$K8_VERSION kubernetes-cni docker-ce --nogpgcheck -y || throw $ConfigException 

        # setup kernel parameters
        modprobe br_netfilter || throw $ConfigException
        sysctl -w net.bridge.bridge-nf-call-iptables=1 -w net.bridge.bridge-nf-call-ip6tables=1 > /etc/sysctl.d/k8s.conf || throw $ConfigException
        sysctl -p /etc/sysctl.d/k8s.conf || throw $ConfigException

        # enable cgroupfs 
        sed -i '/config.yaml/s/config.yaml"/config.yaml --cgroup-driver=cgroupfs"/g' /usr/lib/systemd/system/kubelet.service.d/10-kubeadm.conf || throw $ConfigException

        # enable local docker registry.
        echo '{"insecure-registries":"cortx-docker.colo.seagate.com"}' | jq '.' > /etc/docker/daemon.json

        (systemctl restart docker && systemctl daemon-reload &&  systemctl enable docker) || throw $Exception
        echo "Docker Runtime Configured Successfully"

        (systemctl restart kubelet && systemctl daemon-reload && systemctl enable kubelet) || throw $Exception
        echo "kubelet Configured Successfully"


        #Download calico plugin image
        pushd /var/tmp/
            rm -rf calico*.yaml 
            if [ "$CALICO_PLUGIN_VERSION" == "latest" ]; then 
                curl  https://docs.projectcalico.org/manifests/calico.yaml -o calico-$CALICO_PLUGIN_VERSION.yaml || throw $Exception
            else
                CALICO_PLUGIN_MAJOR_VERSION=$(echo $CALICO_PLUGIN_VERSION | awk -F[.] '{print $1"."$2}')
                curl https://docs.projectcalico.org/archive/$CALICO_PLUGIN_MAJOR_VERSION/manifests/calico.yaml -o calico-$CALICO_PLUGIN_VERSION.yaml || throw $Exception
            fi
            CALICO_IMAGE_VERSION=$(grep 'docker.io/calico/cni' calico-$CALICO_PLUGIN_VERSION.yaml | uniq | awk -F':' '{ print $3}')	
            wget -c https://github.com/projectcalico/calico/releases/download/$CALICO_IMAGE_VERSION/release-$CALICO_IMAGE_VERSION.tgz -O - | tar -xz || throw $Exception
            cd release-$CALICO_IMAGE_VERSION/images && for file in calico-node.tar calico-kube-controllers.tar  calico-cni.tar calico-pod2daemon-flexvol.tar; do docker load -i $file || throw $Exception; done
        popd
    )
    catch || {
    # handle excption
    case $ex_code in
        $Exception)
            echo "An Exception was thrown. Please check logs"
            throw 1
        ;;
        $ConfigException)
            echo "A ConfigException was thrown. Please check logs"
            throw 1
        ;;
        *)
            echo "An unexpected exception was thrown"
            throw $ex_code # you can rethrow the "exception" causing the script to exit if not caught
        ;;
    esac
    }

}

function setup_master_node(){
    local UNTAINT_MASTER=$1
    try
    (
        #cleanup
        echo "y" | kubeadm reset
        
        #initialize cluster
        kubeadm init || throw $Exception

        # Verify node added in cluster
        #kubectl get nodes || throw $Exception

        # Copy cluster configuration for user
        mkdir -p $HOME/.kube
        cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
        chown $(id -u):$(id -g) $HOME/.kube/config
        # untaint master node
        if [ "$UNTAINT_MASTER" == "true" ]; then
            echo "--------------------------[ Allow POD creation on master node ]--------------------------"
            kubectl taint nodes $(hostname) node-role.kubernetes.io/master- || throw $Exception
        fi    

        # Apply calcio plugin 	
        if [ "$CALICO_PLUGIN_VERSION" == "latest" ]; then
            curl https://docs.projectcalico.org/manifests/calico.yaml -o calico-$CALICO_PLUGIN_VERSION.yaml || throw $Exception
        else
            CALICO_PLUGIN_MAJOR_VERSION=$(echo $CALICO_PLUGIN_VERSION | awk -F[.] '{print $1"."$2}')
            curl https://docs.projectcalico.org/archive/$CALICO_PLUGIN_MAJOR_VERSION/manifests/calico.yaml -o calico-$CALICO_PLUGIN_VERSION.yaml || throw $Exception    
        fi
        # Setup IP_AUTODETECTION_METHOD for determining calico network.
        # sed -i '/# Auto-detect the BGP IP address./i \            - name: IP_AUTODETECTION_METHOD\n              value: "interface=eth-0"' calico-$CALICO_PLUGIN_VERSION.yaml
        kubectl apply -f calico-$CALICO_PLUGIN_VERSION.yaml || throw $Exception
        # Setup storage-class
        kubectl apply -f https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml || throw $Exception
        kubectl patch storageclass local-path -p '{"metadata": {"annotations":{"storageclass.kubernetes.io/is-default-class":"true"}}}' || throw $ConfigException

        # Install helm
        curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/master/scripts/get-helm-3 || throw $Exception
        (chmod 700 get_helm.sh && ./get_helm.sh) || throw $Exception
    )
    catch || {
    # Handle excption
    case $ex_code in
        $Exception)
            echo "An Exception was thrown. Please check logs"
            throw 1
        ;;
        $ConfigException)
            echo "A ConfigException was thrown. Please check logs"
            throw 1
        ;;
        *)
            echo "An unexpected exception was thrown"
            throw $ex_code # you can rethrow the "exception" causing the script to exit if not caught
        ;;
    esac
    }    
        
}


ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

case $ACTION in
    --cleanup)
        cleanup_node
    ;;
    --prepare) 
        install_prerequisites
    ;;
    --status) 
        print_cluster_status
    ;;
    --master)
        setup_master_node "$2"
    ;;
    *)
        echo "ERROR : Please provide valid option"
        usage
        exit 1
    ;;    
esac

