#!/bin/bash

set -euf -o pipefail

UDS_DOWNLOAD_URL="$2"
UDS_DOWNLOAD_DIR="/mnt/bigstorage/releases/cortx/uds_uploads/"
ARTIFACTORY_PASSWORD="$1"
COMPONNET_DIR="$3"
ls -ltr $UDS_DOWNLOAD_DIR
[ "${UDS_DOWNLOAD_URL#"${UDS_DOWNLOAD_URL%?}"}" != "/" ] && UDS_DOWNLOAD_URL+="/"
UDS_DOWNLOAD_FOLDER=$(echo $UDS_DOWNLOAD_URL | awk -F '/' '{ print $(NF-1) }')
mkdir -p $UDS_DOWNLOAD_DIR
pushd $UDS_DOWNLOAD_DIR
        /bin/wget -r --no-parent -nd -nc -R index.html --http-user=udx_jenkins_ro --http-password="$ARTIFACTORY_PASSWORD" $UDS_DOWNLOAD_URL -P "$UDS_DOWNLOAD_FOLDER"
        echo "Downloaded UDS Packages at $UDS_DOWNLOAD_FOLDER"
popd

echo "Linking UDS packages"
ls -ltr "$COMPONNET_DIR"/uds/
pushd "$COMPONNET_DIR"/uds/
        rm -f last_successful
        ls -ltr $UDS_DOWNLOAD_DIR/"$UDS_DOWNLOAD_FOLDER"
        ln -s $UDS_DOWNLOAD_DIR/"$UDS_DOWNLOAD_FOLDER" last_successful
popd

