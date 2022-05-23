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

usage() {

echo "Generate CORTX container images from provided CORTX release build"
echo "Usage: $0 [ -b build ] [ -p push docker-image to GHCR yes/no. Default no] [ -t tag latest yes/no. Default no" ] [ -r registry location ] [ -e environment ] [ -o operating-system ][ -s service ] [ -h print help message ] 1>&2; exit 1; }

VERSION=2.0.0
DOCKER_PUSH=no
TAG_LATEST=no
ENVIRONMENT=opensource-ci
REGISTRY="cortx-docker.colo.seagate.com"
PROJECT="seagate"
ARTFACT_URL="http://cortx-storage.colo.seagate.com/releases/cortx/github/"
SERVICE=all
OS=rockylinux-8.4
IMAGE_LIST=( "cortx-all" "cortx-rgw" "cortx-data" "cortx-control" )


while getopts "b:p:t:r:e:o:s:h:" opt; do
    case $opt in
        b ) BUILD=$OPTARG;;
        p ) DOCKER_PUSH=$OPTARG;;
        t ) TAG_LATEST=$OPTARG;;
        e ) ENVIRONMENT=$OPTARG;;
        r ) REGISTRY=$OPTARG;;
        s ) SERVICE=$OPTARG;;
        o ) OS=$OPTARG;;
        h ) usage
        exit 0;;
        *) usage
        exit 1;;
    esac
done

if [ -z "${BUILD}" ] ; then
    BUILD=last_successful_prod
fi

if echo $BUILD | grep -q http;then
        BUILD_URL="$BUILD"
else
        if echo $BUILD | grep -q custom; then BRANCH="integration-custom-ci"; else BRANCH="main"; fi
        BUILD_URL="$ARTFACT_URL/$BRANCH/$OS/$BUILD"
fi

if [ $SERVICE == all ];then
SERVICE=${IMAGE_LIST[@]}
fi

if [ "$SERVICE" == "cortx-rgw" ] && [ "$OS" == "centos-7.9.2009" ]; then
echo -e "#####################################################################"
echo -e "# SERVICE: $SERVICE Image Build is NOT supported on $OS              "
echo -e "#####################################################################"
exit 1
fi

OS_TYPE=$(echo $OS | awk -F[-] '{print $1}')
if [ "$OS_TYPE" == "rockylinux" ]; then OS_TYPE="rockylinux/rockylinux"; fi
OS_RELEASE=$( echo $OS | awk -F[-] '{print $2}')

echo -e "########################################################"
echo -e "# SERVICE    : $SERVICE                                 "
echo -e "# BUILD_URL  : $BUILD_URL                               "
echo -e "# Base OS    : $OS_TYPE                                 "
echo -e "# Base image : $OS_TYPE:$OS_RELEASE                     "
echo -e "########################################################"

function get_git_hash {
sed -i '/KERNEL/d' RELEASE.INFO
for component in cortx-py-utils cortx-s3server cortx-motr cortx-hare cortx-provisioner
do
    echo $component:"$(awk -F['_'] '/'$component'-'$VERSION'/ { print $2 }' RELEASE.INFO | cut -d. -f1 | sed 's/git//g')",
done
echo cortx-csm_agent:"$(awk -F['_'] '/cortx-csm_agent-'$VERSION'/ { print $3 }' RELEASE.INFO | cut -d. -f1)",
}

curl -s $BUILD_URL/RELEASE.INFO -o RELEASE.INFO
if grep -q "404 Not Found" RELEASE.INFO ; then echo -e "\nProvided Build does not have required structure..existing\n"; exit 1; fi

for PARAM in BRANCH BUILD
do
     export DOCKER_BUILD_$PARAM="$(grep $PARAM RELEASE.INFO | cut -d'"' -f2)"
done
GIT_HASH=$(get_git_hash | tr '\n' ' ')
rm -rf RELEASE.INFO

CORTX_VERSION=$(sed 's/^- /, /g' compatibility.info  | tr -d '\n' | sed 's/^, //g')

if [ "$DOCKER_BUILD_BRANCH" != "main" ]; then
        export TAG=$VERSION-$DOCKER_BUILD_BUILD-$DOCKER_BUILD_BRANCH
else
        export TAG=$VERSION-$DOCKER_BUILD_BUILD
fi

CREATED_DATE=$(date -u +'%Y-%m-%d %H:%M:%S%:z')

docker-compose -f ./docker-compose.yml build --parallel --force-rm --compress --build-arg GIT_HASH="$GIT_HASH" --build-arg VERSION="$VERSION-$DOCKER_BUILD_BUILD" --build-arg CREATED_DATE="$CREATED_DATE" --build-arg BUILD_URL=$BUILD_URL --build-arg ENVIRONMENT=$ENVIRONMENT --build-arg OS=$OS --build-arg OS_TYPE=$OS_TYPE --build-arg OS_RELEASE=$OS_RELEASE --build-arg CORTX_VERSION="$CORTX_VERSION" $SERVICE

for SERVICE_NAME in $SERVICE
do
if [ "$DOCKER_PUSH" == "yes" ];then
        echo "Pushing Docker image to GitHub Container Registry"
        docker tag $SERVICE_NAME:$TAG $REGISTRY/$PROJECT/$SERVICE_NAME:$TAG
        docker push $REGISTRY/$PROJECT/$SERVICE_NAME:$TAG
else
        echo "Docker Image push skipped"
        exit 0
fi
done

for SERVICE_NAME in $SERVICE
do
if [ "$TAG_LATEST" == "yes" ];then
        echo "Tagging generated image as latest"
	docker tag "$(docker images $REGISTRY/$PROJECT/$SERVICE_NAME --format='{{.Repository}}:{{.Tag}}' | head -1)" $REGISTRY/$PROJECT/$SERVICE_NAME:"${TAG//$DOCKER_BUILD_BUILD/latest}"        
	docker push $REGISTRY/$PROJECT/$SERVICE_NAME:"${TAG//$DOCKER_BUILD_BUILD/latest}"
else
        echo "Latest tag creation skipped"
fi
done
