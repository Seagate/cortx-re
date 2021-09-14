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

usage() { echo "Usage: $0 [-b build ]  [-p push docker-image to GHCR yes/no. Default no] [ -t tag latest yes/no. Default no" ] 1>&2; exit 1; }

VERSION=2.0.0
DOCKER_PUSH=no
TAG_LATEST=no
OS=centos-7.9.2009
ARTFACT_URL="http://cortx-storage.colo.seagate.com/releases/cortx/github/"


while getopts "b:p:t:" opt; do
    case $opt in
        b ) BUILD=$OPTARG;;
        p ) DOCKER_PUSH=$OPTARG;;
        t ) TAG_LATEST=$OPTARG;;
        h ) usage
        exit 0;;
        *) usage
        exit 1;;
    esac
done

if [ -z "${BUILD}" ] ; then
    BUILD=last_successful_prod
fi

if echo $BUILD | grep -q custom; then BRANCH="integration-custom-ci"; else BRANCH="main"; fi
BUILD_URL="$ARTFACT_URL/$BRANCH/$OS/$BUILD"

echo "Building cortx-all image from $BUILD_URL"
sleep 5

function get_git_hash {

for component in cortx-py-utils cortx-s3server cortx-motr cortx-hare
do
    echo $component:$(awk -F['_'] '/'$component'-2.0.0/ { print $2 }' RELEASE.INFO | cut -d. -f1 | sed 's/git//g'),
done
echo cortx-csm_agent:$(awk -F['_'] '/cortx-csm_agent-2.0.0/ { print $3 }' RELEASE.INFO | cut -d. -f1),
echo cortx-prvsnr:$(awk -F['.'] '/cortx-prvsnr-2.0.0/ { print $4 }' RELEASE.INFO | sed 's/git//g'),
}


curl -s $BUILD_URL/RELEASE.INFO -o RELEASE.INFO
if grep -q "404 Not Found" RELEASE.INFO ; then echo "Provided Build does not have required structure..existing"; exit 1; fi

for PARAM in BRANCH BUILD
do
export DOCKER_BUILD_$PARAM=$(grep $PARAM RELEASE.INFO | cut -d'"' -f2)
done
CORTX_VERSION=$(get_git_hash | tr '\n' ' ')
rm -rf RELEASE.INFO

pushd ../.././
if [ "$DOCKER_BUILD_BRANCH" != "stable" ]; then
	export TAG=$VERSION-$DOCKER_BUILD_BUILD-$DOCKER_BUILD_BRANCH
else
	export TAG=$VERSION-$DOCKER_BUILD_BUILD
fi

CREATED_DATE=$(date -u +'%Y-%m-%d %H:%M:%S%:z')

docker-compose -f docker/cortx-deploy/docker-compose.yml build --force-rm --compress --build-arg GIT_HASH="$CORTX_VERSION" --build-arg CREATED_DATE="$CREATED_DATE" --build-arg BUILD_URL=$BUILD_URL  cortx-all

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
