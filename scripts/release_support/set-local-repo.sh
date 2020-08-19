#!/bin/bash

RELEASE=$2
UPLOADS="uploads"
GITHUB_TOKEN=$1
REPO="seagate/cortx-re"
RELEASE_REPO_LOCATION="/var/tmp/$RELEASE"
UPLOADS_REPO_LOCATION="/var/tmp/uploads"
RELEASE_REPO_FILE="cortx-$RELEASE.repo"


function _create_local_repo () {
        REPO_LOCATION=$1
        DOWNLOAD_RELEASE=$2
        rm -rf $REPO_LOCATION
        mkdir $REPO_LOCATION && cd $REPO_LOCATION
        githubrelease --github-token $GITHUB_TOKEN asset $REPO download $DOWNLOAD_RELEASE
        createrepo -v .
}

function _create_local_repo_file () {
rm -rf $LOCAL_REPO_FILE
cat << EOF >> $LOCAL_REPO_FILE
[releases_$RELEASE]
name=Cortx $RELEASE  Repository
baseurl=file://$RELEASE_REPO_LOCATION
gpgkey=file://$RELEASE_REPO_LOCATION/RPM-GPG-KEY-Seagate
gpgcheck=1
enabled=1

[uploads]
name=Cortx Uploads Repository
baseurl=file://$UPLOADS_REPO_LOCATION
gpgcheck=0
enabled=1
EOF
}

function _local_repo_validation() {

cp -f $LOCAL_REPO_FILE /etc/yum.repos.d/
yum clean all; rm -rf /var/cache/yum
rpm -ev cortx-prvsnr-cli
yum install -y cortx-prvsnr-cli gmock kibana


}


_create_local_repo $RELEASE_REPO_LOCATION $RELEASE
_create_local_repo $UPLOADS_REPO_LOCATION $UPLOADS
_create_local_repo_file
_local_repo_validation

