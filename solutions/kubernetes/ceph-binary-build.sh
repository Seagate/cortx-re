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

ACTION="$1"
BUILD_LOCATION="$2"
MOUNT="$3"
REPO_COMPONENT=${CEPH_REPO##*/}
REPO_COMPONENT="${REPO_COMPONENT%.*}"

function usage() {
    cat << HEREDOC
Usage : $0 [--ceph-build] [BUILD_LOCATION]
where,
    --ceph-build - Build ceph binary packages.
    --ceph-build-env - Build ceph binary packages inside container environment.
    BUILD_LOCATION - Build location on disk.
HEREDOC
}

if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

function check_params() {
    add_primary_separator "\t\tChecking parameters"
    if [ -z "$CEPH_REPO" ]; then echo "CEPH_REPO not provided. Using default: ceph/ceph ";CEPH_REPO="https://github.com/ceph/ceph"; fi
    if [ -z "$CEPH_BRANCH" ]; then echo "CEPH_BRANCH not provided. Using default: quincy";CEPH_BRANCH="quincy"; fi
    if [ -z "$BUILD_OS" ]; then echo "BUILD_OS not provided. Using default: centos";BUILD_OS="centos"; fi
    if [ -z "$BUILD_LOCATION" ]; then echo "BUILD_LOCATION for container to mount not provided. Using default: /var/log/ceph-build/${BUILD_NUMBER}";BUILD_LOCATION="/var/log/ceph-build/${BUILD_NUMBER}"; fi
    if [ -z "$REPO_COMPONENT" ]; then echo "REPO_COMPONENT for ceph repo not provided. Using default: ceph";REPO_COMPONENT="ceph"; fi
    if [ -z "$CORTX_RGW_OPTIMIZED_BUILD" ]; then echo "CORTX_RGW_OPTIMIZED_BUILD for ceph packages not provided. Using default: false";CORTX_RGW_OPTIMIZED_BUILD="false"; fi
    if [ -z "$INSTALL_MOTR" ]; then echo "INSTALL_MOTR for ceph build not provided. Using default: false";INSTALL_MOTR="false"; fi
    if [ -z "$PR_BUILD" ]; then echo "PR_BUILD for ceph build not provided. Using default: false";PR_BUILD="false"; fi
    if [ -z "$PR_ID" ]; then echo "PR_ID for ceph build not provided. Using default: false";PR_ID="0"; fi
    if [ -z "$MOUNT" ]; then echo "MOUNT for uploading packages is not provided. Using default: cortx-storage.colo.seagate.com:/mnt/data1/releases/ceph";MOUNT="cortx-storage.colo.seagate.com:/mnt/data1/releases/ceph"; fi
    if [ -z "$build_upload_dir" ]; then echo "build_upload_dir for ceph packages not provided. Using default: /mnt/bigstorage/releases/ceph";build_upload_dir="/mnt/bigstorage/releases/ceph"; fi

   echo -e "\n\n########################################################################"
   echo -e "# CEPH_REPO                 : $CEPH_REPO                  "
   echo -e "# CEPH_BRANCH               : $CEPH_BRANCH                "
   echo -e "# BUILD_OS                  : $BUILD_OS                   "
   echo -e "# BUILD_LOCATION            : $BUILD_LOCATION             "
   echo -e "# REPO_COMPONENT            : $REPO_COMPONENT             "
   echo -e "# CORTX_RGW_OPTIMIZED_BUILD : $CORTX_RGW_OPTIMIZED_BUILD  "
   echo -e "# INSTALL_MOTR              : $INSTALL_MOTR               "
   echo -e "# PR_BUILD                  : $PR_BUILD                   "
   echo -e "# PR_ID                     : $PR_ID                      "
   echo -e "# MOUNT                     : $MOUNT                      "
   echo -e "# build_upload_dir          : $build_upload_dir           "
   echo -e "#########################################################################"
}

function prereq() {
    add_primary_separator "\t\tRunning Prerequisites"

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
        docker run --rm -t -e CEPH_REPO="$CEPH_REPO" -e CEPH_BRANCH="$CEPH_BRANCH" -e BUILD_OS="$BUILD_OS" -e REPO_COMPONENT="$REPO_COMPONENT" -e BUILD_LOCATION="/home" -e CORTX_RGW_OPTIMIZED_BUILD="$CORTX_RGW_OPTIMIZED_BUILD" -e branch="$branch" -e os_version="$os_version" -e release_tag="$release_tag" -e INSTALL_MOTR=$INSTALL_MOTR -e PR_BUILD="$PR_BUILD" -e PR_ID="$PR_ID" -v "$BUILD_LOCATION/$BUILD_OS":/home --name "$REPO_COMPONENT-$BUILD_NUMBER" --entrypoint /bin/bash ubuntu:20.04 -c "pushd /home && ./build.sh --env-build && popd"

    elif [[ "$BUILD_OS" == "centos-8" ]]; then
        if [[ $(docker images --format "{{.Repository}}:{{.Tag}}" --filter reference=centos:8) != "centos:8" ]]; then
            docker pull centos:8
        fi
        docker pull quay.io/centos/centos:stream8
        add_secondary_separator "Run CentOS 8 container and run build script"
        # docker run --rm -t -e CEPH_REPO=$CEPH_REPO -e CEPH_BRANCH=$CEPH_BRANCH -e BUILD_OS=$BUILD_OS -e REPO_COMPONENT=$REPO_COMPONENT -e BUILD_LOCATION="/home" -v "$BUILD_LOCATION/$BUILD_OS":/home --name "$REPO_COMPONENT-$BUILD_NUMBER" --entrypoint /bin/bash centos:8 -c "pushd /home && ./build.sh --env-build && popd"
        docker run --rm -t -e CEPH_REPO="$CEPH_REPO" -e CEPH_BRANCH="$CEPH_BRANCH" -e BUILD_OS="$BUILD_OS" -e REPO_COMPONENT="$REPO_COMPONENT" -e BUILD_LOCATION="/home" -e CORTX_RGW_OPTIMIZED_BUILD="$CORTX_RGW_OPTIMIZED_BUILD" -e branch="$branch" -e os_version="$os_version" -e release_tag="$release_tag" -e INSTALL_MOTR=$INSTALL_MOTR -e PR_BUILD="$PR_BUILD" -e PR_ID="$PR_ID" -v "$BUILD_LOCATION/$BUILD_OS":/home --name "$REPO_COMPONENT-$BUILD_NUMBER" --entrypoint /bin/bash quay.io/centos/centos:stream8 -c "pushd /home && ./build.sh --env-build && popd"

    elif [[ "$BUILD_OS" == "rockylinux-8.4" ]]; then
        if [[ $(docker images --format "{{.Repository}}:{{.Tag}}" --filter reference=rockylinux:8) != "rockylinux:8" ]]; then
            docker pull rockylinux:8
        fi
        add_secondary_separator "Run Rocky Linux 8 container and run build script"
        docker run --rm -t -e CEPH_REPO="$CEPH_REPO" -e CEPH_BRANCH="$CEPH_BRANCH" -e BUILD_OS="$BUILD_OS" -e REPO_COMPONENT="$REPO_COMPONENT" -e BUILD_LOCATION="/home" -e CORTX_RGW_OPTIMIZED_BUILD="$CORTX_RGW_OPTIMIZED_BUILD" -e branch="$branch" -e os_version="$os_version" -e release_tag="$release_tag" -e INSTALL_MOTR=$INSTALL_MOTR -e PR_BUILD="$PR_BUILD" -e PR_ID="$PR_ID" -v "$BUILD_LOCATION/$BUILD_OS":/home --name "$REPO_COMPONENT-$BUILD_NUMBER" --entrypoint /bin/bash rockylinux:8 -c "pushd /home && ./build.sh --env-build && popd"

    else
        add_secondary_separator "Failed to build ceph, container image not present."
        exit 1
    fi
}

function ceph_build() {
    add_primary_separator "\t\tStart Ceph Build"

    case "$BUILD_OS" in
        ubuntu-20.04)
                add_primary_separator "Building Ubuntu ceph binary packages"
                add_common_separator "Update repolist cache and install prerequisites"
                apt update && apt install git -y
                check_status
                DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends tzdata
                check_status
                pushd "$BUILD_LOCATION"
                    add_common_separator "Clone Repo"
                    git clone $CEPH_REPO
                    check_status

                    if [[ "$INSTALL_MOTR" == true ]]; then
                        add_common_separator "cortx-motr dependencies no yet sorted on ubuntu"
                        exit 1
                    fi

                    pushd $REPO_COMPONENT
                        if [[ "$PR_BUILD" == true ]]; then
                            git fetch origin pull/"$PR_ID"/head:"pr-$PR_ID"
                            check_status
                            git checkout "pr-$PR_ID"
                            check_status
                            git log -1 --oneline
                            check_status

                        else
                            git checkout $CEPH_BRANCH
                            check_status
                            git log -1 --oneline
                            check_status
                        fi

                        echo "Checked out: $(git branch --show-current)"

                        add_common_separator "Checkout Submodules"
                        git submodule update --init --recursive

                        add_common_separator "Install Dependencies"
                        ./install-deps.sh
                        check_status "install-deps.sh script execution failed."

                        add_common_separator "Make Source Tarball"
                        ./make-dist
                        check_status "make-dist script execution failed."
                        
                        mv ceph-*tar.bz2 ../
                        version=$(git describe --long --match 'v*' | sed 's/^v//')
                    popd

                    tar -xf ceph-*tar.bz2
                    pushd ceph-"$version"
                        add_common_separator "Start Build"
                        dpkg-buildpackage -us -uc
                        check_status "Build failed"
                    popd

                    add_common_separator "List generated binary packages (*.deb)"
                    ls *.deb
                popd
        ;;
        centos-8)
                add_primary_separator "Building centos binary packages"
                add_common_separator "Update repolist cache and install prerequisites"
                # rpm -ivh http://mirror.centos.org/centos/8-stream/BaseOS/x86_64/os/Packages/centos-gpg-keys-8-3.el8.noarch.rpm
                # check_status
                yum install dnf -y
                # dnf --disablerepo '*' --enablerepo=extras swap centos-linux-repos centos-stream-repos -y
                check_status
                yum makecache && yum install git -y
                check_status
                yum install yum-utils epel-release wget bzip2 rpm-build rpmdevtools dnf-plugins-core -y
                check_status
                dnf config-manager --set-enabled powertools
                pushd "$BUILD_LOCATION"
                    add_common_separator "Clone Repo"
                    git clone $CEPH_REPO
                    check_status

                    if [[ "$INSTALL_MOTR" == true ]]; then
                        add_common_separator "Installing cortx-motr dependencies"
                        yum-config-manager --add-repo="http://cortx-storage.colo.seagate.com/releases/cortx/github/$branch/$os_version/$release_tag/cortx_iso/"
                        yum-config-manager --add-repo="http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/rockylinux/rockylinux-8.4-2.0.0-latest/"
                        yum install cortx-motr{,-devel} -y --nogpgcheck
                    fi

                    pushd $REPO_COMPONENT
                        if [[ "$PR_BUILD" == true ]]; then
                            git fetch origin pull/"$PR_ID"/head:"pr-$PR_ID"
                            check_status
                            git checkout "pr-$PR_ID"
                            check_status
                            git log -1 --oneline
                            check_status

                        else
                            git checkout $CEPH_BRANCH
                            check_status
                            git log -1 --oneline
                            check_status
                        fi

                        echo "Checked out: $(git branch --show-current)"

                        add_common_separator "Checkout Submodules"
                        git submodule update --init --recursive

                        add_common_separator "Install Dependencies"
                        ./install-deps.sh
                        check_status "install-deps.sh script execution failed."

                        add_common_separator "Make Source Tarball"
                        ./make-dist
                        check_status "make-dist script execution failed."
                        
                        mkdir -p ../rpmbuild/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
                        tar --strip-components=1 -C ../rpmbuild/SPECS --no-anchored -xvjf ceph-*tar.bz2 "ceph.spec"
                        mv ceph*tar.bz2 ../rpmbuild/SOURCES/
                    popd

                    pushd rpmbuild
                        add_common_separator "Start Build"
                        if [[ "$CORTX_RGW_OPTIMIZED_BUILD" == true ]]; then
                            rpmbuild --clean --rmsource --define "_unpackaged_files_terminate_build 0" --define "debug_package %{nil}" --define "_binary_payload w2T16.xzdio" --define "_topdir $(pwd)" --without seastar --without cephfs_java --without ceph_test_package --without selinux --without lttng  --without cephfs_shell  --without amqp_endpoint --without kafka_endpoint --without lua_packages --without zbd --without cmake_verbose_logging --without rbd_rwl_cache --without rbd_ssd_cache  --without system_pmdk --without jaeger --without ocf --without make_check -vv -ba SPECS/ceph.spec
                            check_status "Build failed"
                        else
                            rpmbuild --define "_topdir `pwd`" -ba SPECS/ceph.spec
                            check_status "Build failed"
                        fi
                    popd

                    add_common_separator "List generated binary packages (*.rpm)"
                    ls rpmbuild/RPMS/*
                popd
        ;;
        rockylinux-8.4)
                add_primary_separator "Building rocky linux binary packages"
                add_common_separator "Update repolist cache and install prerequisites"
                yum makecache && yum install git -y
                check_status
                yum install yum-utils epel-release wget bzip2 rpm-build dnf rpmdevtools dnf-plugins-core -y
                check_status
                dnf config-manager --set-enabled powertools
                pushd "$BUILD_LOCATION"
                    add_common_separator "Clone Repo"
                    git clone $CEPH_REPO
                    check_status

                    if [[ "$INSTALL_MOTR" == true ]]; then
                        add_common_separator "Installing cortx-motr dependencies"
                        yum-config-manager --add-repo="http://cortx-storage.colo.seagate.com/releases/cortx/github/$branch/$os_version/$release_tag/cortx_iso/"
                        check_status "Failed to add motr repo"
                        yum-config-manager --add-repo="http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/rockylinux/rockylinux-8.4-2.0.0-latest/"
                        check_status "Failed to add motr repo"
                        yum install cortx-motr{,-devel} -y --nogpgcheck
                        check_status "Failed to install cortx-motr"
                    fi

                    pushd $REPO_COMPONENT
                        if [[ "$PR_BUILD" == true ]]; then
                            git fetch origin pull/"$PR_ID"/head:"pr-$PR_ID"
                            check_status
                            git checkout "pr-$PR_ID"
                            check_status
                            git log -1 --oneline
                            check_status

                        else
                            git checkout $CEPH_BRANCH
                            check_status
                            git log -1 --oneline
                            check_status
                        fi

                        echo "Checked out: $(git branch --show-current)"

                        add_common_separator "Checkout Submodules"
                        git submodule update --init --recursive

                        if [[ "$(git rev-parse --abbrev-ref HEAD)" == "quincy" ]]; then
                            sed -i 's/centos|fedora|rhel|ol|virtuozzo/centos|fedora|rhel|ol|virtuozzo|rocky/g' install-deps.sh
                            sed -i 's/centos|rhel|ol|virtuozzo/centos|rhel|ol|virtuozzo|rocky/g' install-deps.sh
                        fi

                        add_common_separator "Install Dependencies"
                        ./install-deps.sh
                        check_status "install-deps.sh script execution failed."

                        add_common_separator "Make Source Tarball"
                        ./make-dist
                        check_status "make-dist script execution failed."
                        
                        mkdir -p ../rpmbuild/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
                        tar --strip-components=1 -C ../rpmbuild/SPECS --no-anchored -xvjf ceph-*tar.bz2 "ceph.spec"
                        mv ceph*tar.bz2 ../rpmbuild/SOURCES/
                    popd

                    pushd rpmbuild
                        add_common_separator "Start Build"
                        if [[ "$CORTX_RGW_OPTIMIZED_BUILD" == true ]]; then
                            rpmbuild --clean --rmsource --define "_unpackaged_files_terminate_build 0" --define "debug_package %{nil}" --define "_binary_payload w2T16.xzdio" --define "_topdir $(pwd)" --without seastar --without cephfs_java --without ceph_test_package --without selinux --without lttng  --without cephfs_shell  --without amqp_endpoint --without kafka_endpoint --without lua_packages --without zbd --without cmake_verbose_logging --without rbd_rwl_cache --without rbd_ssd_cache  --without system_pmdk --without jaeger --without ocf --without make_check -vv -ba SPECS/ceph.spec
                            check_status "Build failed"
                        else
                            rpmbuild --define "_topdir `pwd`" -ba SPECS/ceph.spec
                            check_status "Build failed"
                        fi
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
        echo "cortx-storage.colo.seagate.com:/mnt/data1/releases/ceph is not mounted. Mounting..."
        sudo mount -t nfs4 "$MOUNT" "$build_upload_dir"
        check_status "Mounting $MOUNT to $build_upload_dir has failed."
    fi

    add_secondary_separator "Uploading Binary Packages to CORTX-Storage"
    pushd "$build_upload_dir"
        mkdir -p "$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
    popd

    if [[ "$VM_BUILD" = true ]]; then
        case "$BUILD_OS" in
            ubuntu-20.04)
                pushd "$BUILD_LOCATION"
                    cp ./*.deb "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    check_status
                popd

                add_secondary_separator "List files after upload"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    ls -la *.deb
                popd

                add_secondary_separator "Create Repo"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    apt-get install -y dpkg-dev
                    dpkg-scanpackages . /dev/null | gzip -9c > Packages.gz
                popd

                add_secondary_separator "Tag Last Successful"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH"
                    test -d last_successful && unlink last_successful
                    ln -s "$BUILD_NUMBER" last_successful
                popd
            ;;
            centos-8)
                pushd "$BUILD_LOCATION/rpmbuild"
                    cp RPMS/*/*.rpm "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    check_status
                popd

                add_secondary_separator "List files after upload"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    ls -la *
                popd

                add_secondary_separator "Create Repo"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    rpm -qi createrepo || yum install -y createrepo
                    createrepo .
                popd

                add_secondary_separator "Tag Last Successful"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH"
                    test -d last_successful && unlink last_successful
                    ln -s "$BUILD_NUMBER" last_successful
                popd

            ;;
            rockylinux-8.4)
                pushd "$BUILD_LOCATION/rpmbuild"
                    cp RPMS/*/*.rpm "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    check_status
                popd

                add_secondary_separator "List files after upload"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    ls -la *
                popd
                
                add_secondary_separator "Create Repo"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    rpm -qi createrepo || yum install -y createrepo
                    createrepo .
                popd

                add_secondary_separator "Tag Last Successful"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH"
                    test -d last_successful && unlink last_successful
                    ln -s "$BUILD_NUMBER" last_successful
                popd
            ;;
        esac
    
    else
        case "$BUILD_OS" in
            ubuntu-20.04)
                pushd "$BUILD_LOCATION/$BUILD_OS"
                    cp ./*.deb "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    check_status
                popd

                add_secondary_separator "List files after upload"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    ls -la *.deb
                popd

                add_secondary_separator "Create Repo"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    apt-get install -y dpkg-dev
                    dpkg-scanpackages . /dev/null | gzip -9c > Packages.gz
                popd

                add_secondary_separator "Tag Last Successful"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH"
                    test -d last_successful && unlink last_successful
                    ln -s "$BUILD_NUMBER" last_successful
                popd
            ;;
            centos-8)
                pushd "$BUILD_LOCATION/$BUILD_OS/rpmbuild"
                    cp RPMS/*/*.rpm "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    check_status
                popd

                add_secondary_separator "List files after upload"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    ls -la *
                popd

                add_secondary_separator "Create Repo"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    rpm -qi createrepo || yum install -y createrepo
                    createrepo .
                popd

                add_secondary_separator "Tag Last Successful"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH"
                    test -d last_successful && unlink last_successful
                    ln -s "$BUILD_NUMBER" last_successful
                popd

            ;;
            rockylinux-8.4)
                pushd "$BUILD_LOCATION/$BUILD_OS/rpmbuild"
                    cp RPMS/*/*.rpm "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    check_status
                popd

                add_secondary_separator "List files after upload"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    ls -la *
                popd
                
                add_secondary_separator "Create Repo"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH/$BUILD_NUMBER"
                    rpm -qi createrepo || yum install -y createrepo
                    createrepo .
                popd

                add_secondary_separator "Tag Last Successful"
                pushd "$build_upload_dir/$REPO_COMPONENT/$BUILD_OS/$CEPH_BRANCH"
                    test -d last_successful && unlink last_successful
                    ln -s "$BUILD_NUMBER" last_successful
                popd
            ;;
        esac
    fi
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