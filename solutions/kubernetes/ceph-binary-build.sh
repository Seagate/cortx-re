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

source functions.sh
source /etc/os-release

BUILD_LOCATION="$2"
MOUNT="$3"

function usage() {
    cat << HEREDOC
Usage : $0 [--ceph-build] [BUILD_LOCATION]
where,
    --ceph-build - Build ceph binary packages.
    --ceph-build-env - Build ceph binary packages inside container environment.
    BUILD_LOCATION - Build location on disk.
HEREDOC
}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

function check_params() {
    add_primary_separator "Checking parameters"
    if [ -z "$CEPH_REPO" ]; then echo "CEPH_REPO not provided. Using default: ceph/ceph ";CEPH_REPO="ceph/ceph"; fi
    if [ -z "$CEPH_BRANCH" ]; then echo "CEPH_BRANCH not provided. Using default: quincy";CEPH_BRANCH="quincy"; fi
    if [ -z "$BUILD_OS" ]; then echo "BUILD_OS not provided. Using default: centos";BUILD_OS="centos"; fi
    if [ -z "$BUILD_LOCATION" ]; then echo "BUILD_LOCATION for container to mount not provided. Using default: /var/log/ceph-build";BUILD_LOCATION="/var/log/ceph-build"; fi
    if [ -z "$MOUNT" ]; then echo "MOUNT for uploading packages is not provided. Using default: cortx-storage.colo.seagate.com:/mnt/data1/releases/ceph";MOUNT="cortx-storage.colo.seagate.com:/mnt/data1/releases/ceph"; fi

   echo -e "\n\n########################################################################"
   echo -e "# CEPH_REPO         : $CEPH_REPO                  "
   echo -e "# CEPH_BRANCH       : $CEPH_BRANCH                "
   echo -e "# BUILD_OS          : $BUILD_OS                   "
   echo -e "# BUILD_LOCATION    : $BUILD_LOCATION             "
   echo -e "# MOUNT             : $MOUNT                      "
   echo -e "#########################################################################"
}

function prereq() {
    add_primary_separator "\t\tRunning Preequisites"

    mkdir -p "$BUILD_LOCATION"

    pushd "$BUILD_LOCATION"
        add_common_separator "Removing previous files"
        rm -rvf *
    popd

    if [[ "$ACTION" == "--ceph-build-env" ]]; then
        add_secondary_separator "Copy build scripts to $BUILD_LOCATION/$BUILD_OS"
        mkdir -p "$BUILD_LOCATION/$BUILD_OS"
        cp $0 "$BUILD_LOCATION/$BUILD_OS/build.sh"
        cp functions.sh "$BUILD_LOCATION/$BUILD_OS"
    fi
}

function prvsn_env() {
    add_primary_separator "\tProvision $BUILD_OS Build Environment"

    add_secondary_separator "Verify docker installation"
    if ! which docker; then
        add_common_separator "Installing Docker on Build Node Agent"
        curl -fsSL https://get.docker.com -o get-docker.sh
        chmod +x get-docker.sh
        ./get-docker.sh
    fi

    if [[ "$BUILD_OS" == "ubuntu-20.04" ]]; then
        if [[ $(docker images --format "{{.Repository}}:{{.Tag}}" --filter reference=ubuntu:20.04) != "ubuntu:20.04" ]]; then
            docker pull ubuntu:20.04
        fi
        add_secondary_separator "Run Ubuntu 20.04 container and run build script"
        docker run --rm -t -e CEPH_REPO=$CEPH_REPO -e CEPH_BRANCH=$CEPH_BRANCH -e BUILD_LOCATION="/home" --name ceph_ubuntu -v "$BUILD_LOCATION/$BUILD_OS":/home --entrypoint /bin/bash ubuntu:20.04 -c "pushd /home && ./build.sh --env-build && popd"

    elif [[ "$BUILD_OS" == "centos-8" ]]; then
        if [[ $(docker images --format "{{.Repository}}:{{.Tag}}" --filter reference=centos:8) != "centos:8" ]]; then
            docker pull centos:8
        fi
        add_secondary_separator "Run CentOS 8 container and run build script"
        docker run --rm -t -e CEPH_REPO=$CEPH_REPO -e CEPH_BRANCH=$CEPH_BRANCH  -e BUILD_LOCATION="/home" --name ceph_centos -v "$BUILD_LOCATION/$BUILD_OS":/home --entrypoint /bin/bash centos:8 -c "pushd /home && ./build.sh --env-build && popd"

    elif [[ "$BUILD_OS" == "rockylinux-8.4" ]]; then
        if [[ $(docker images --format "{{.Repository}}:{{.Tag}}" --filter reference=rockylinux:8) != "rockylinux:8" ]]; then
            docker pull rockylinux:8
        fi
        add_secondary_separator "Run Rocky Linux 8 container and run build script"
        docker run --rm -t -e CEPH_REPO=$CEPH_REPO -e CEPH_BRANCH=$CEPH_BRANCH  -e BUILD_LOCATION="/home" --name ceph_rockylinux -v "$BUILD_LOCATION/$BUILD_OS":/home --entrypoint /bin/bash rockylinux:8 -c "pushd /home && ./build.sh --env-build && popd"

    else
        add_secondary_separator "Failed to build ceph, container image not present."
        exit 1
    fi
}

function ceph_build() {
    add_primary_separator "\t\tStart Ceph Build"

    case "$ID" in
        ubuntu)
            add_primary_separator "Building Ubuntu ceph binary packages"
            add_common_separator "Update repolist cache and install prerequisites"
            apt update && apt install git -y
            DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends tzdata
            pushd "$BUILD_LOCATION"
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
                    version=$(git describe --long --match 'v*' | sed 's/^v//')
                popd

                tar -xf ceph-*tar.bz2
                pushd ceph-"$version"
                    add_common_separator "Start Build"
                    dpkg-buildpackage -us -uc
                popd

                add_common_separator "List generated binary packages (*.deb)"
                ls *.deb
            popd
        ;;
        centos)
                add_primary_separator "Building centos binary packages"
                add_common_separator "Update repolist cache and install prerequisites"
                rpm -ivh http://mirror.centos.org/centos/8-stream/BaseOS/x86_64/os/Packages/centos-gpg-keys-8-3.el8.noarch.rpm
                dnf --disablerepo '*' --enablerepo=extras swap centos-linux-repos centos-stream-repos -y
                yum makecache && yum install git -y
                yum install wget bzip2 rpm-build rpmdevtools dnf-plugins-core -y
                dnf config-manager --set-enabled powertools
                pushd "$BUILD_LOCATION"
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

                    pushd rpmbuild
                        add_common_separator "Start Build"
                        rpmbuild --define "_topdir `pwd`" -ba SPECS/ceph.spec
                    popd

                    add_common_separator "List generated binary packages (*.rpm)"
                    ls rpmbuild/RPMS/*
                popd
        ;;
        rocky)
                add_primary_separator "Building rocky linux binary packages"
                add_common_separator "Update repolist cache and install prerequisites"
                yum makecache && yum install git -y
                yum install wget bzip2 rpm-build rpmdevtools dnf-plugins-core -y
                dnf config-manager --set-enabled powertools
                pushd "$BUILD_LOCATION"
                    add_common_separator "Clone Repo"
                    git clone $CEPH_REPO -b $CEPH_BRANCH

                    pushd ceph
                        add_common_separator "Checkout Submodules"
                        git submodule update --init --recursive

                        if [[ "$(git rev-parse --abbrev-ref HEAD)" == "quincy" ]]; then
                            sed -i 's/centos|fedora|rhel|ol|virtuozzo/centos|fedora|rhel|ol|virtuozzo|rocky/g' install-deps.sh
                            sed -i 's/centos|rhel|ol|virtuozzo/centos|rhel|ol|virtuozzo|rocky/g' install-deps.sh
                        fi

                        add_common_separator "Install Dependencies"
                        ./install-deps.sh

                        add_common_separator "Make Source Tarball"
                        ./make-dist
                        
                        mkdir -p ../rpmbuild/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
                        tar --strip-components=1 -C ../rpmbuild/SPECS --no-anchored -xvjf ceph-*tar.bz2 "ceph.spec"
                        mv ceph*tar.bz2 ../rpmbuild/SOURCES/
                    popd

                    pushd rpmbuild
                        add_common_separator "Start Build"
                        rpmbuild --define "_topdir `pwd`" -ba SPECS/ceph.spec
                    popd

                    add_common_separator "List generated binary packages (*.rpm)"
                    ls rpmbuild/RPMS/*
                popd
        ;;
    esac
}

function upload_packages() {
    add_primary_separator "Upload Binary Packages to CORTX-Storage"
    mkdir -p "$build_upload_dir"

    add_secondary_separator "Check CORTX-Storage Mountpoint"
    grep -qs "$MOUNT" /proc/mounts;
    if grep -qs "$MOUNT" /proc/mounts; then
        echo "cortx-storage.colo.seagate.com:/mnt/data1/releases/ceph is mounted."
    else
        echo "cortx-storage.colo.seagate.com:/mnt/data1/releases/ceph is not mounted."
        sudo mount -t nfs4 "$MOUNT" "$build_upload_dir"
        check_status
    fi

    add_secondary_separator "Uploading Binary Packages to CORTX-Storage"
    pushd "$build_upload_dir"
        mkdir -p "$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
    popd

    case "$BUILD_OS" in
        ubuntu-20.04)
            pushd "$BUILD_LOCATION/$BUILD_OS"
                cp *.deb "$build_upload_dir/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                check_status
            popd

            add_secondary_separator "List files after upload"
            pushd "$build_upload_dir/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                ls -la *.deb
            popd

            add_secondary_separator "Create Repo"
            pushd "$build_upload_dir/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                apt-get install -y dpkg-dev
                dpkg-scanpackages . /dev/null | gzip -9c > Packages.gz
            popd

            add_secondary_separator "Tag Last Successful"
            pushd "$build_upload_dir/$BUILD_OS/$CEPH_BRANCH"
                test -d last_successful && unlink last_successful
                ln -s "$BUILD_NUMBER" last_successful
            popd
        ;;
        centos-8)
            pushd "$BUILD_LOCATION/$BUILD_OS/rpmbuild"
                cp RPMS/*/*.rpm "$build_upload_dir/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                check_status
            popd

            add_secondary_separator "List files after upload"
            pushd "$build_upload_dir/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                ls -la *
            popd

            add_secondary_separator "Create Repo"
            pushd "$build_upload_dir/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                rpm -qi createrepo || yum install -y createrepo
                createrepo .
            popd

            add_secondary_separator "Tag Last Successful"
            pushd "$build_upload_dir/$BUILD_OS/$CEPH_BRANCH"
                test -d last_successful && unlink last_successful
                ln -s "$BUILD_NUMBER" last_successful
            popd

        ;;
        rockylinux-8.4)
            pushd "$BUILD_LOCATION/$BUILD_OS/rpmbuild"
                cp RPMS/*/*.rpm "$build_upload_dir/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                check_status
            popd

            add_secondary_separator "List files after upload"
            pushd "$build_upload_dir/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                ls -la *
            popd
            
            add_secondary_separator "Create Repo"
            pushd "$build_upload_dir/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                rpm -qi createrepo || yum install -y createrepo
                createrepo .
            popd

            add_secondary_separator "Tag Last Successful"
            pushd "$build_upload_dir/$BUILD_OS/$CEPH_BRANCH"
                test -d last_successful && unlink last_successful
                ln -s "$BUILD_NUMBER" last_successful
            popd
        ;;
    esac
}

case $ACTION in
    --ceph-build)
        check_params
        prereq
        ceph_build
    ;;
    --ceph-build-env)
        check_params
        prereq
        prvsn_env
    ;;
    --env-build)
        ceph_build
    ;;
    --upload-packages)
        upload_packages
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;
esac