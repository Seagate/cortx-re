#!/bin/bash
#
# Copyright (c) 2022 Seagate Technology LLC and/or its Affiliates
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

REPO_ROOT=$PWD/../..
source $REPO_ROOT/solutions/kubernetes/functions.sh
BRANCH="main"

function usage() {
    cat << HEREDOC
Usage : $0 [--branch]
where,
    --branch - provide spesific branch.
HEREDOC
}

while getopts "b:h:" opt; do
    case $opt in
        b ) BRANCH=$OPTARG;;
        h ) usage
        exit 0;;
        *) usage
        exit 1;;
    esac
done


IP=$(ip route get 8.8.8.8| cut -d' ' -f7|awk '!/^$/')
DOCKER_VERSION=latest

function docker_check() {
        docker --version >/dev/null 2>&1
        if [ $? -eq 0 ]; then
                add_common_separator "Installed Docker version: $(docker --version)."
        else
                add_common_separator "Docker is not installed. Installing Docker engine"
                rm -rf /etc/yum.repos.d/download.docker.com_linux_centos_7_x86_64_stable_.repo docker-ce.repo
                yum install -y yum-utils && yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo -y && yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
                sleep 30
                systemctl start docker
        fi
}
docker_check

function docker_compose_check() {
        docker-compose --version >/dev/null 2>&1
        if [ $? -eq 0 ]; then
                add_common_separator "Installed Docker-Compose version: $(docker-compose --version)."
        else
                add_common_separator "Docker-compose not installed"
                add_primary_separator "Installing Docker-compose"
                curl -SL https://github.com/docker/compose/releases/download/v2.5.0/docker-compose-linux-x86_64 -o /usr/local/bin/docker-compose
                chmod +x /usr/local/bin/docker-compose
                ln -s /usr/local/bin/docker-compose /usr/bin/docker-compose
                docker-compose --version
        fi
}
docker_compose_check

#Install git
yum install git -y

# Compile and Build CORTX Stack
docker rmi --force $(docker images --filter=reference='*/*/cortx-build:*' --filter=reference='*cortx-build:*' -q)
docker pull ghcr.io/seagate/cortx-build:rockylinux-8.4

# Clone the CORTX repository
pushd /mnt && git clone https://github.com/Seagate/cortx --recursive --depth=1

# Checkout main branch for generating CORTX packages
docker run --rm -v /mnt/cortx:/cortx-workspace ghcr.io/seagate/cortx-build:rockylinux-8.4 make checkout BRANCH=$BRANCH

# Validate CORTX component clone status
pushd /mnt/cortx/ 
for component in cortx-motr cortx-hare cortx-rgw-integration cortx-manager cortx-utils cortx-ha cortx-rgw
do 
echo -e "\n==[ Checking git branch for $component ]=="
pushd $component
git status | egrep -iw 'Head|modified|Untracked'
    if [ $? -eq 0 ]; then
       add_common_separator "Git status pending"
        exit 1
    else    
        popd
    fi    
done && pushd -

# Build the CORTX packages
docker run --rm -v /var/artifacts:/var/artifacts -v /mnt/cortx:/cortx-workspace ghcr.io/seagate/cortx-build:rockylinux-8.4 make clean cortx-all-rockylinux-image

function packet_validation() {
        ls -l /var/artifacts/0 | egrep -iw '3rd_party|python_deps'
        if [ $? -eq 0 ]; then
            add_common_separator "Required packages are available"
        else
            add_common_separator "Required packages are not available to proceed further"
            exit 1
        fi
}
packet_validation

# Nginx container creation with required configuration
docker run --name release-packages-server -v /var/artifacts/0/:/usr/share/nginx/html:ro -d -p 80:80 nginx

function nginx_validation() {
        sleep 60
        docker ps | grep -iw 'nginx'
        if [ $? -eq 0 ]; then
            add_common_separator "Nginx is running"
            curl -L http://$IP/RELEASE.INFO
        else
            exit 1
        fi    
}
nginx_validation
# clone cortx-re repository & run build.sh
git clone https://github.com/Seagate/cortx-re && pushd cortx-re/docker/cortx-deploy/
./build.sh -b http://$IP -o rockylinux-8.4 -s all -e opensource-ci

# Show recently generated cortx-all images
docker images --format='{{.Repository}}:{{.Tag}} {{.CreatedAt}}' --filter=reference='cortx-*'
