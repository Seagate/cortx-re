#!/bin/bash

function DockerCheck() {
        docker --version >/dev/null 2>&1
        if [ $? -eq 0 ]; then
                echo "Docker is installed and $(docker --version)."
        else
                echo "Docker not installed"
                printf "\n################################################################################\n"
                printf "\tInstalling Docker Engine\n"
                printf "\n################################################################################\n"
                yum install -y yum-utils
                yum-config-manager \
    --add-repo \
    https://download.docker.com/linux/centos/docker-ce.repo -y
                yum install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
                systemctl start docker
                docker --version
#               exit 1
        fi
}
DockerCheck

function DockerComposeCheck() {
        docker-compose --version >/dev/null 2>&1
        if [ $? -eq 0 ]; then
                echo "Docker-Compose is installed and $(docker-compose --version)."
        else
                echo "Docker-Compose not installed"
                printf "\n################################################################################\n"
                printf "\tInstalling Docker-Compose\n"
                printf "\n################################################################################\n"
                curl -SL https://github.com/docker/compose/releases/download/v2.5.0/docker-compose-linux-x86_64 -o /usr/local/bin/docker-compose
                chmod +x /usr/local/bin/docker-compose
                ln -s /usr/local/bin/docker-compose /usr/bin/docker-compose
                docker-compose --version
#               exit 1
        fi
}
DockerComposeCheck

# Compile and Build CORTX Stack
docker pull ghcr.io/seagate/cortx-build:rockylinux-8.4

# Clone the CORTX repository
cd /mnt && git clone https://github.com/Seagate/cortx --recursive --depth=1

# Checkout main branch for generating CORTX packages
docker run --rm -v /mnt/cortx:/cortx-workspace ghcr.io/seagate/cortx-build:rockylinux-8.4 make checkout BRANCH=main

# Validate CORTX component clone status
cd /mnt/cortx/ 
for component in cortx-motr cortx-hare cortx-rgw-integration cortx-manager cortx-utils cortx-ha cortx-rgw
do 
echo -e "\n==[ Checking Git Branch for $component ]=="
pushd $component
git status | egrep -iw 'Head|modified|Untracked'
    if [ $? -eq 0 ]; then
        echo "GIT STATUS PENDING"
        exit 1
    else    
        popd
    fi    
done && cd -

# Build the CORTX packages
docker run --rm -v /var/artifacts:/var/artifacts -v /mnt/cortx:/cortx-workspace ghcr.io/seagate/cortx-build:rockylinux-8.4 make clean cortx-all-rockylinux-image

function PacketValidation() {
        ls -l /var/artifacts/0 | egrep -iw '3rd_party|python_deps'
        if [ $? -eq 0 ]; then
            echo "Required Packages Are Available"
        else
            echo "Required Packages Are Not Available To Proceed Further"
            exit 1
        fi
}
PacketValidation

# Nginx container creation with required configuration
docker run --name release-packages-server -v /var/artifacts/0/:/usr/share/nginx/html:ro -d -p 80:80 nginx

function NginxValidation() {
        docker ps | grep -iw 'nginx'
        if [ $? -eq 0 ]; then
            curl -L http://$(ip route get 8.8.8.8| cut -d' ' -f7)/RELEASE.INFO
        else
            docker restart nginx
            sleep 10
            docker ps | grep -iw 'nginx'
                if [ $? -eq 0 ]; then
                    curl -L http://$(ip route get 8.8.8.8| cut -d' ' -f7)/RELEASE.INFO
                else
                    docker run --name release-packages-server -v /var/artifacts/0/:/usr/share/nginx/html:ro -d -p 80:80 nginx
                    docker ps | grep -iw 'nginx'
                        if [ $? -eq 0 ]; then
                            curl -L http://$(ip route get 8.8.8.8| cut -d' ' -f7)/RELEASE.INFO
                        else
                            exit 1
                        fi                
                fi
        fi        
}
NginxValidation

# clone cortx-re repository & run build.sh

git clone https://github.com/Seagate/cortx-re && cd cortx-re/docker/cortx-deploy/
./build.sh -b http://$HOSTNAME -o rockylinux-8.4 -s all -e opensource-ci
#sed -i "/^[[:space:]].*TAG/a\    extra_hosts:\n      - \"$(ip route get 8.8.8.8| cut -d' ' -f7)"\" docker-compose.yml
#cat docker-compose.yml | egrep -iw "extra_hosts|$(ip route get 8.8.8.8| cut -d' ' -f7)"
#        if [ $? -eq 0 ]; then
#            ./build.sh -b http://$(ip route get 8.8.8.8| cut -d' ' -f7) -o rockylinux-8.4 -s all -e opensource-ci
#        else
#            sed -i "/^[[:space:]].*TAG/a\    extra_hosts:\n      - \"$(ip route get 8.8.8.8| cut -d' ' -f7)"\" docker-compose.yml
#            ./build.sh -b http://$(ip route get 8.8.8.8| cut -d' ' -f7) -o rockylinux-8.4 -s all -e opensource-ci
#        fi

# Show recently generated cortx-all images
docker images --format='{{.Repository}}:{{.Tag}} {{.CreatedAt}}' --filter=reference='cortx-*'