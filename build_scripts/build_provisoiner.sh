#!/bin/bash

set -x

#set environment
RELEASE=1.0.0
VERSION=1

#Execute scripts to build packages
echo "Building Provisioner Packages"
ls -ltr
pushd build/provisioner
sh ./devops/rpms/buildrpm.sh -g $(git rev-parse --short HEAD) -e $RELEASE -b $VERSION
sh ./cli/buildrpm.sh -g $(git rev-parse --short HEAD) -e $RELEASE -b $VERSION
mkdir release/ && cp /root/rpmbuild/RPMS/x86_64/*.rpm release/
