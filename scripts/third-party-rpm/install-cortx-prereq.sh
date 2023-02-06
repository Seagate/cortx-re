#!/bin/bash

usage() { echo "Usage: $0 [ -b build_url] [ -r RPM location]" 1>&2; exit 1; }

#Define Default values
BUILD_URL="http://ssc-nfs-cicd1.colo.seagate.com/releases/cortx/github/main/centos-7.8.2003/last_successful_prod/"
RPM_LOCATION=remote

while getopts "b:r:" opt; do
    case $opt in
        b ) BUILD_URL=$OPTARG;;
        r ) RPM_LOCATION=$OPTARG;;
        h ) usage
        exit 0;;
        *) usage
        exit 1;;
    esac
done

echo " Using following values:"
echo "BUILD URL:"  "$BUILD_URL"

#Setup repositories and install packages
yum install yum-utils -y

yum-config-manager --add-repo="$BUILD_URL"/3rd_party/
yum-config-manager --add-repo="$BUILD_URL"/cortx_iso/

yum-config-manager --save --setopt=ssc-nfs-cicd1*.gpgcheck=1 ssc-nfs-cicd1* && yum-config-manager --save --setopt=ssc-nfs-cicd1*.gpgcheck=0 ssc-nfs-cicd1*

cat <<EOF >/etc/pip.conf
[global]
timeout: 60
index-url: $BUILD_URL/python_deps
trusted-host: ssc-nfs-cicd1.colo.seagate.com
EOF

yum clean all && rm -rf /var/cache/yum
if [ "$RPM_LOCATION" == "remote" ]; then
    yum install cortx-prereq -y
else
    yum install /root/rpmbuild/RPMS/x86_64/*.rpm -y
fi

#Cleanup
rm -rf  /etc/yum.repos.d/ssc-nfs-cicd1.colo.seagate.com_releases_cortx_* /etc/pip.conf