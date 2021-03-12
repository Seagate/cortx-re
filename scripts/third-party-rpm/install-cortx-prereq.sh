#!/bin/bash

usage() { echo "Usage: $0 [ -b branch] [ -t third-party-version] [ -p python-deps-version] [ -o os-version ]" 1>&2; exit 1; }

#Define Default values
BRANCH=main
OS_VERSION=centos-7.8.2003
THIRD_PARTY_VERSION=centos-7.8.2003-2.0.0-latest
PYTHON_DEPS_VERSION=python-packages-2.0.0-latest

while getopts "tpboh" opt; do
    case $opt in
        b ) BRANCH=$OPTARG;;
        t ) THIRD_PARTY_VERSION=$OPTARG;;
        p ) PYTHON_DEPS_VERSION=$OPTARG;;
        o ) OS_VERSION=$OPTARG;;
        h ) usage
        exit 0;;
        *) usage
        exit 1;;
    esac
done

#Setup repositories and install packages
yum install yum-utils -y
yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/centos/$THIRD_PARTY_VERSION/
yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/$BRANCH/$OS_VERSION/last_successful_prod/cortx_iso/
yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*

cat <<EOF >/etc/pip.conf
[global]
timeout: 60
index-url: http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/python-deps/$PYTHON_DEPS_VERSION/
trusted-host: cortx-storage.colo.seagate.com
EOF

yum install java-1.8.0-openjdk-headless -y && yum install cortx-prereq -y

#Cleanup
rm -rf  /etc/yum.repos.d/cortx-storage.colo.seagate.com_releases_cortx_* /etc/pip.conf