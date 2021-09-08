#!/bin/bash
#
# Copyright (c) 2020 Seagate Technology LLC and/or its Affiliates
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

set -e -o pipefail

usage() { echo "Usage: $0 [-b build url] [-p push docker-image to GHCR yes/no. Default yes] [ -t tag latest yes/no. Default no" ] 1>&2; exit 1; }

VERSION=2.0.0
DOCKER_PUSH=yes
TAG_LATEST=no

while getopts "b:p:t:" opt; do
    case $opt in
        b ) BUILD_URL=$OPTARG;;
        p ) DOCKER_PUSH=$OPTARG;;
        t ) TAG_LATEST=$OPTARG;;
        h ) usage
        exit 0;;
        *) usage
        exit 1;;
    esac
done

if [ -z "${BUILD_URL}" ] ; then
    usage
fi

curl $BUILD_URL/RELEASE.INFO -o RELEASE.INFO

for PARAM in BRANCH BUILD
do
export DOCKER_BUILD_$PARAM=$(grep $PARAM RELEASE.INFO | cut -d'"' -f2)
done
rm -rf RELEASE.INFO

pushd ../.././
if [ "$DOCKER_BUILD_BRANCH" != "stable" ]; then
	export TAG=$VERSION-$DOCKER_BUILD_BUILD-$DOCKER_BUILD_BRANCH
else
	export TAG=$VERSION-$DOCKER_BUILD_BUILD
fi

CREATED_DATE=$(date -u +'%Y-%m-%d %H:%M:%S%:z')

docker-compose -f docker/cortx-deploy/docker-compose.yml build --force-rm --no-cache --compress --build-arg GIT_HASH="$(git rev-parse --short HEAD)" --build-arg CREATED_DATE="$CREATED_DATE" --build-arg BUILD_URL=$BUILD_URL  cortx-all

if [ "$DOCKER_PUSH" == "yes" ];then
        echo "Pushing Docker image to GitHub Container Registry"
	docker-compose -f docker/cortx-deploy/docker-compose.yml push cortx-all
else
	echo "Docker Image push skipped"
	exit 0	
fi
popd

if [ "$TAG_LATEST" == "yes" ];then
	echo "Tagging generated image as latest"
        docker tag $(docker images ghcr.io/seagate/cortx-all --format='{{.Repository}}:{{.Tag}}' | head -1) ghcr.io/seagate/cortx-all:$(echo $TAG | sed 's|'$DOCKER_BUILD_BUILD'|latest|g')
        docker push ghcr.io/seagate/cortx-all:$(echo $TAG | sed 's|'$DOCKER_BUILD_BUILD'|latest|g')  
else 
	echo "Latest tag creation skipped"
fi
