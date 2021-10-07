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

    
CMD=${1}
PREFIX=${2:-/usr/local/bin}
USER=${3:-minikube}
USER_PASS=${4}
USER_HOME="/local/home"
export Exception=100
export ConfigException=101


function usage(){
    cat << HEREDOC
Usage : $0  [install|setup|remove] <install prefix/path> <minikube user>"
where,
    <install prefix/path> - Path for kubectl and minikube installation. Default is /usr/local/bin 
    <minikube user> - User for minikube installation
HEREDOC
}

if [ -z $1 ]; then
    echo "ERROR: No parameters passed"
    usage
fi

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

function install_prereq () {
    try
    (
        echo "Attempting to install minikube and assorted tools to $PREFIX"
        if ! command docker >/dev/null 2>&1; then
            rm -rf /etc/yum.repos.d/download.docker.com_linux_centos_7_x86_64_stable_.repo /etc/yum.repos.d/packages.cloud.google.com_yum_repos_kubernetes-el7-x86_64.repo
            yum-config-manager --add https://download.docker.com/linux/centos/7/x86_64/stable/ || throw $ConfigException
            yum install docker-ce --nogpgcheck -y || throw $Exception
            systemctl restart docker || throw $Exception
            sleep 10
            #chgrp docker /var/run/docker.sock || throw $Exception
        else
            echo "Docker is already installed"
        fi    

        if ! command kubectl >/dev/null 2>&1; then
            version=$(curl -s https://storage.googleapis.com/kubernetes-release/release/stable.txt)
            echo "Installing kubectl version $version"
            curl -LO "https://storage.googleapis.com/kubernetes-release/release/$version/bin/linux/amd64/kubectl" || throw $Exception
            chmod +x kubectl
            mv kubectl "$PREFIX"
        else
            echo "kubetcl is already installed"
        fi

        if ! command minikube >/dev/null 2>&1; then
            curl -Lo minikube https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64 || throw $Exception
            chmod +x minikube
            mv minikube "$PREFIX"
        else
            echo "minikube is already installed"
        fi
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

function setup_cluster () {
    try
    (
        echo "Setting up minikube cluster"
        mkdir -p "${USER_HOME}"
        (useradd -d "${USER_HOME}/${USER}" "${USER}" && (echo "${USER}:${USER_PASS}" | chpasswd))  || throw $Exception
        usermod -aG docker "${USER}"  || throw $Exception
        echo "${USER_PASS}" | su -c "minikube start --driver=docker --alsologtostderr -v=3" "${USER}"
        if [ $? -eq 0 ]; then
            sleep 30
            echo "---------------------------------------[ Cluster Status ]--------------------------------------"
            (echo "${USER_PASS}" | su -c "minikube kubectl -- get po -A" "${USER}")  || throw $Exception 
        else
            echo "ERROR : Minikube setup failed"    
            exit 1
        fi
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

function cleanup_setup () {
    echo "Removing minikube and assorted tools from $PREFIX"
    files_to_remove=(
        "${PREFIX}/kubectl"
        "${PREFIX}/minikube"
        "${PREFIX}/localkube"
        "${USER_HOME}/${USER}/.kube"
        "${USER_HOME}/${USER}/.minikube"
        "/etc/kubernetes/"
    )
    if id -u "${USER}" >/dev/null 2>&1; then
        userdel -f "${USER}"
        rm -rf "${USER_HOME}/${USER}"
    fi
    systemctl stop '*kubelet*.mount'
    docker system prune -af --volumes
    if command docker >/dev/null 2>&1; then
        yum remove docker-ce docker-ce-cli containerd.io -y
        rm -rf "/var/lib/docker"
        rm -rf "/etc/docker"
    fi
    for file in ${files_to_remove[@]}; do
        if [ -f "$file" ] || [ -d "$file" ]; then
            echo "Removing file/folder $file"
            rm -rf $file
        fi
    done
}

case $CMD in
  install)
    install_prereq
    ;;
  setup)
    setup_cluster
    ;;  
  cleanup)
    cleanup_setup
    ;;
  *)
    echo "ERROR : Unknown parameters passed"
    usage
    ;;
esac