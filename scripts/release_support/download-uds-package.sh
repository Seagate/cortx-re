#!/bin/bash

set -euf -o pipefail

UDS_DOWNLOAD_URL="$3"
UDS_DOWNLOAD_DIR="/root/uds"
ARTIFACTORY_USER="$1"
ARTIFACTORY_PASSWORD="$2"

export UDS_DOWNLOAD_FOLDER=$(echo $UDS_DOWNLOAD_URL | awk -F '/' '{ print $(NF-1) }')
mkdir -p $UDS_DOWNLOAD_DIR
pushd $UDS_DOWNLOAD_DIR
rm -rf ud*.rpm
/bin/wget -r --no-parent -nd -R index.html --http-user=udx_jenkins_ro --http-password=Testit123 $UDS_DOWNLOAD_URL -P $UDS_DOWNLOAD_FOLDER

popd


