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

usage(){
    cat << HEREDOC
Usage : $0 [--prepare, --master]
where,
    --prepare - Install prerequisites on nodes for kubernetes setup
    --master - Initialize K8 master node. 
HEREDOC
}


install_prerequisites(){
    # disable swap 
    sudo swapoff -a
    # keeps the swaf off during reboot
    sudo sed -i '/ swap / s/^\(.*\)$/#\1/g' /etc/fstab

    # disable selinux
    setenforce 0
    sed -i  -e 's/SELINUX=enforcing/SELINUX=disabled/g' -e 's/SELINUX=enforcing/SELINUX=permissive/g' /etc/sysconfig/selinux
   
    # stop and disable firewalld
    systemctl stop firewalld && systemctl disable firewalld && sudo systemctl mask --now firewalld

    # set yum repositories for k8 and docker-ce
    rm -rf /etc/yum.repos.d/download.docker.com_linux_centos_7_x86_64_stable_.repo /etc/yum.repos.d/packages.cloud.google.com_yum_repos_kubernetes-el7-x86_64.repo
    yum-config-manager --add https://packages.cloud.google.com/yum/repos/kubernetes-el7-x86_64
    yum-config-manager --add https://download.docker.com/linux/centos/7/x86_64/stable/    

    yum install kubeadm-1.19.0-0 kubectl-1.19.0-0 kubelet-1.19.0-0 kubernetes-cni docker-ce --nogpgcheck -y 

    # setup kernel parameters
    sysctl -w net.bridge.bridge-nf-call-iptables=1 -w net.bridge.bridge-nf-call-ip6tables=1 >> /etc/sysctl.d/k8s.conf
    sysctl -p 

    # enable cgroupfs 
    sed -i '/config.yaml/s/config.yaml"/config.yaml --cgroup-driver=cgroupfs"/g' /usr/lib/systemd/system/kubelet.service.d/10-kubeadm.conf

    sudo systemctl enable docker && sudo systemctl daemon-reload && sudo systemctl restart docker
    echo "Docker Runtime Configured Successfully"

    systemctl enable kubelet && sudo systemctl daemon-reload && systemctl restart kubelet
    echo "kubelet Configured Successfully"


    #Download calico plugin image
    pushd /var/tmp/ 
    wget -c https://github.com/projectcalico/calico/releases/download/v3.20.0/release-v3.20.0.tgz -O - | tar -xz
    cd release-v3.20.0/images && for file in calico-node.tar calico-kube-controllers.tar  calico-cni.tar calico-pod2daemon-flexvol.tar; do docker load -i $file; done
    popd

}

master_node(){
    #cleanup
    echo "y" | kubeadm reset
    rm -rf $HOME/.kube 

    #initialize cluster
    kubeadm init

    # Verify node added in cluster
    kubectl get nodes

    # Copy cluster configuration for user
    mkdir -p $HOME/.kube
    cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
    chown $(id -u):$(id -g) $HOME/.kube/config

    # Apply calcio plugin 	
    kubectl apply -f https://docs.projectcalico.org/manifests/calico.yaml
}


ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

case $ACTION in
    --prepare) 
        install_prerequisites
    ;;
    --master)
        master_node
    ;;    
    *)
        echo "ERROR : Please provide valid option"
        usage
        exit 1
    ;;    
esac

