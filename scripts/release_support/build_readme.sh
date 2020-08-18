#/bin/bash
BUILD_LOCATION=$1
echo -e "Generating README.txt file"
pushd $BUILD_LOCATION
cat <<EOF > README.txt
CONTENTS OF THIS FILE
---------------------

 * Introduction
 * Artifacts
 * Provisioner Guide
 * Known Issues


Introduction
------------

Cortx Details to be added here.


Artifacts
------------
Release Build location:

http://cortx-storage.colo.seagate.com/releases/eos/github/release/rhel-7.7.1908/$ENV/$BUILD_NUMBER/

Release Info file location:

http://cortx-storage.colo.seagate.com/releases/eos/github/release/rhel-7.7.1908/$ENV/$BUILD_NUMBER/RELEASE.INFO

Installation
-----------------

Provisioner Guide

https://github.com/Seagate/cortx-prvsnr/wiki/QuickStart-Guide 

Known Issues
------------

Known issues are tracked at

https://github.com/Seagate/cortx-prvsnr/wiki/Deploy-Stack#known-deployment-issues

EOF

