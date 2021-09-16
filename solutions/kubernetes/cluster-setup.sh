#!/bin/bash
#
# Copyright (c) 2020 Seagate Technology LLC and/or its Affiliates
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
Usage : $0 [--prepare]
where,
    --prepare - Install prerequisites on nodes for kubernetes setup
HEREDOC
}

install_prerequisites(){
    # disable swap 
    sudo swapoff -a
    # keeps the swaf off during reboot
    sudo sed -i '/ swap / s/^\(.*\)$/#\1/g' /etc/fstab

    # disable selinux
    setenforce 0
    sed -i --follow-symlinks 's/SELINUX=enforcing/SELINUX=disabled/g' /etc/sysconfig/selinux

    systemctl stop firewalld
    systemctl disable firewalld
    systemctl mask --now firewalld

    yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo

    cat <<EOF > /etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=https://packages.cloud.google.com/yum/repos/kubernetes-el7-x86_64
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=https://packages.cloud.google.com/yum/doc/yum-key.gpg https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
EOF

    yum install kubeadm docker-ce -y

    sudo systemctl enable docker
    sudo systemctl daemon-reload
    sudo systemctl restart docker
    echo "Docker Runtime Configured Successfully"

    systemctl enable kubelet
    systemctl restart kubelet
    echo "kubelet Configured Successfully"

}

initialize_cluster(){
    #initialize cluster
    kubeadm init
    # Verify node added in cluster
    kubectl get nodes
    # Copy cluster configuration for user
    mkdir -p $HOME/.kube
    cp -i /etc/kubernetes/admin.conf $HOME/.kube/config
    chown $(id -u):$(id -g) $HOME/.kube/config
    # deploy calico network
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
    --init)
        initialize_cluster
    ;;    
    *)
        echo "ERROR : Please provide valid option"
        usage
        exit 1
    ;;    
esac