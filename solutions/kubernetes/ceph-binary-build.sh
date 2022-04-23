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

######################################################################
# This script is specific for the reserved HW by RE team.
# To use on other HW/VM please install docker and change
# container volume $MOUNT_LOCATION for build os container.
######################################################################

source functions.sh

MOUNT_LOCATION="/var/log/ceph-build"

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

function usage() {
    cat << HEREDOC
Usage : $0 [--build-ubuntu, --build-centos, --build-rockylinux]
where,
    --build-ubuntu - Build Ubuntu binary packages (*.debs).
    --build-centos  - Build CentOS binary packages (*.rpms).
    --build-rockylinux - Build Rocky Linux binary packages (*.rpms).
HEREDOC
}

function check_params() {
    if [ -z "$CEPH_REPO" ]; then echo "CEPH_REPO not provided. Using default: ceph/ceph ";CEPH_REPO="ceph/ceph"; fi
    if [ -z "$CEPH_BRANCH" ]; then echo "CEPH_BRANCH not provided. Using default: quincy";CEPH_BRANCH="quincy"; fi

   echo -e "\n\n########################################################################"
   echo -e "# CEPH_REPO         : $CEPH_REPO                  "
   echo -e "# CEPH_BRANCH       : $CEPH_BRANCH                "
   echo -e "#########################################################################"
}

function prereq() {
    add_primary_separator "\t\tPreparing Build node"

    # Verify docker installation:
    add_secondary_separator "Verify docker installation"
    if ! which docker; then
        add_secondary_separator "Please install Docker on Build Node Agent"
        exit 1
    fi
}

function build_ubuntu() {
    add_primary_separator "Building ubuntu binary packages"

    add_secondary_separator "Verfiy/pull Ubuntu docker image"
    if [[ $(docker images | tail -n+2 | awk '{ print $1":"$2 }' | grep ubuntu) != "ubuntu:latest" ]]; then
        docker pull ubuntu:latest
    fi

    add_secondary_separator "Make build directory and run Ubuntu container"
    mkdir -p $MOUNT_LOCATION/ubuntu
    docker run --rm -t -d --name ceph_ubuntu -v /$MOUNT_LOCATION/ubuntu:/home ubuntu:latest

    pushd $MOUNT_LOCATION/ubuntu
        add_common_separator "Removing previous files"
        rm -rvf *
    popd

    add_secondary_separator "Write build script"
    cp functions.sh $MOUNT_LOCATION/ubuntu
    pushd $MOUNT_LOCATION/ubuntu
        cat << EOF > build.sh
#/bin/bash

source /home/functions.sh

add_common_separator "Update repolist cache and install prerequisites"
apt update && apt install git -y
DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends tzdata
pushd /home/
    add_common_separator "Clone Repo"
    git clone $CEPH_REPO -b $CEPH_BRANCH

    pushd ceph
        add_common_separator "Checkout Submodules"
        git submodule update --init --recursive

        add_common_separator "Install Dependencies"
        ./install-deps.sh

        add_common_separator "Make Source Tarball"
        ./make-dist
        
        mv ceph-*tar.bz2 ../
        version=\$(git describe --long --match 'v*' | sed 's/^v//')
    popd

    tar -xf ceph-*tar.bz2    
    pushd /home/ceph-"\$version"
        add_common_separator "Start Build"
        dpkg-buildpackage -us -uc
    popd

    add_common_separator "List generated binary packages (*.deb)"
    ls *.deb
popd
EOF

    chmod +x build.sh
    popd

    add_secondary_separator "Run build script"
    docker exec ceph_ubuntu bash /home/build.sh

    add_primary_separator "\t\tDestroy build environment"
    docker stop ceph_ubuntu
}

function build_centos() {
    add_primary_separator "Building centos binary packages"

    add_secondary_separator "Verfiy/pull CentOS docker image"
    if [[ $(docker images | tail -n+2 | awk '{ print $1":"$2 }' | grep centos) != "centos:latest" ]]; then
        docker pull centos:latest
    fi

    add_secondary_separator "Make build directory and run CentOS container"
    mkdir -p $MOUNT_LOCATION/centos
    docker run --rm -t -d --name ceph_centos -v /$MOUNT_LOCATION/centos:/home centos:latest

    pushd $MOUNT_LOCATION/centos
        add_common_separator "Removing previous files"
        rm -rvf *
    popd

    add_secondary_separator "Write build script"
    cp functions.sh $MOUNT_LOCATION/centos
    pushd $MOUNT_LOCATION/centos

        cat << EOF > build.sh
#/bin/bash

source /home/functions.sh

add_common_separator "Update repolist cache and install prerequisites"
rpm -ivh http://mirror.centos.org/centos/8-stream/BaseOS/x86_64/os/Packages/centos-gpg-keys-8-3.el8.noarch.rpm
dnf --disablerepo '*' --enablerepo=extras swap centos-linux-repos centos-stream-repos -y
yum makecache && yum install git -y
yum install wget bzip2 rpm-build rpmdevtools dnf-plugins-core -y
dnf config-manager --set-enabled powertools
pushd /home/
    add_common_separator "Clone Repo"
    git clone $CEPH_REPO -b $CEPH_BRANCH

    pushd ceph
        add_common_separator "Checkout Submodules"
        git submodule update --init --recursive

        add_common_separator "Install Dependencies"
        ./install-deps.sh

        add_common_separator "Make Source Tarball"
        ./make-dist
        
        mkdir -p ../rpmbuild/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
        tar --strip-components=1 -C ../rpmbuild/SPECS --no-anchored -xvjf ceph-*tar.bz2 "ceph.spec"
        mv ceph*tar.bz2 ../rpmbuild/SOURCES/
    popd

    pushd rpmbuild/
        add_common_separator "Start Build"
        rpmbuild --define "_topdir /home/rpmbuild" -ba SPECS/ceph.spec
    popd

    add_common_separator "List generated binary packages (*.rpm)"
    ls rpmbuild/RPMS/*
popd
EOF

    chmod +x build.sh
    popd

    add_secondary_separator "Run build script"
    docker exec ceph_centos bash /home/build.sh

    add_primary_separator "\t\tDestroy build environment"
    docker stop ceph_centos
}

function build_rockylinux() {
    add_primary_separator "Building rocky linux binary packages"

    add_secondary_separator "Verfiy/pull Rocky Linux docker image"
    if [[ $(docker images | tail -n+2 | awk '{ print $1":"$2 }' | grep rockylinux) != "rockylinux:latest" ]]; then
        docker pull rockylinux:latest
    fi
    
    add_secondary_separator "Make build directory and run Rocky Linux container"
    mkdir -p $MOUNT_LOCATION/rockylinux
    docker run --rm -t -d --name ceph_rockylinux -v /$MOUNT_LOCATION/rockylinux:/home rockylinux:latest

    pushd $MOUNT_LOCATION/rockylinux
        add_common_separator "Removing previous files"
        rm -rvf *
    popd

    add_secondary_separator "Write build script"
    cp functions.sh $MOUNT_LOCATION/rockylinux
    pushd $MOUNT_LOCATION/rockylinux
        cat << EOF > build.sh
#/bin/bash

source /home/functions.sh

add_common_separator "Update repolist cache and install prerequisites"
yum makecache && yum install git -y
yum install wget bzip2 rpm-build rpmdevtools dnf-plugins-core -y
dnf config-manager --set-enabled powertools
pushd /home/
    add_common_separator "Clone Repo"
    git clone $CEPH_REPO -b $CEPH_BRANCH

    pushd ceph
        add_common_separator "Checkout Submodules"
        git submodule update --init --recursive

        sed -i 's/centos|fedora|rhel|ol|virtuozzo/centos|fedora|rhel|ol|virtuozzo|rocky/g' install-deps.sh
        sed -i 's/centos|rhel|ol|virtuozzo/centos|rhel|ol|virtuozzo|rocky/g' install-deps.sh

        add_common_separator "Install Dependencies"
        ./install-deps.sh

        add_common_separator "Make Source Tarball"
        ./make-dist
        
        mkdir -p ../rpmbuild/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
        tar --strip-components=1 -C ../rpmbuild/SPECS --no-anchored -xvjf ceph-*tar.bz2 "ceph.spec"
        mv ceph*tar.bz2 ../rpmbuild/SOURCES/
    popd

    pushd rpmbuild/
        add_common_separator "Start Build"
        rpmbuild --define "_topdir /home/rpmbuild" -ba SPECS/ceph.spec
    popd

    add_common_separator "List generated binary packages (*.rpm)"
    ls rpmbuild/RPMS/*
popd
EOF

    chmod +x build.sh
    popd

    add_secondary_separator "Run build script"
    docker exec ceph_rockylinux bash /home/build.sh

    add_primary_separator "\t\tDestroy build environment"
    docker stop ceph_rockylinux
}

case $ACTION in
    --build-ubuntu)
        check_params
        prereq
        build_ubuntu
    ;;
    --build-centos)
        check_params
        prereq
        build_centos
    ;;
    --build-rockylinux)
        check_params
        prereq
        build_rockylinux
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;
esac