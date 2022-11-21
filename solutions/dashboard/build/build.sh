#!/bin/bash
#
# Copyright (c) 2022 Seagate Technology LLC and/or its Affiliates
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

echo "Generate Container Images for Dashboard Application"
echo "Usage: $0 [ -b build ] [ -p push docker-image to GHCR yes/no. Default no] [ -t tag latest yes/no. Default no" ] [ -r registry location ] [ -e environment ] [ -o operating-system ][ -s service ] [ -h print help message ] 1>&2; exit 1; }

VERSION=1.0.0
DOCKER_PUSH=no
TAG_LATEST=no
ENVIRONMENT=opensource-ci
REGISTRY="cortx-docker.colo.seagate.com"
PROJECT="dashboard"
SERVICE=all
#OS=rockylinux-8.4
IMAGE_LIST=( "codacy" "portscanner" "github")


while getopts "b:p:t:r:e:o:s:h:" opt; do
    case $opt in
        b ) BUILD=$OPTARG;;
        p ) DOCKER_PUSH=$OPTARG;;
        t ) TAG_LATEST=$OPTARG;;
        e ) ENVIRONMENT=$OPTARG;;
        r ) REGISTRY=$OPTARG;;
        s ) SERVICE=$OPTARG;;
        h ) usage
        exit 0;;
        *) usage
        exit 1;;
    esac
done

[ -z $BUILD ] && BUILD=0

if [ $SERVICE == all ];then SERVICE=${IMAGE_LIST[@]}; fi

echo -e "########################################################"
echo -e "# SERVICE    : $SERVICE                                 "
echo -e "# BUILD_URL  : $BUILD_URL                               "
echo -e "# Registry   : $REGISTRY                                "
echo -e "########################################################"

function setup_local_image_registry {
        docker rm -f local-registry || { echo "Failed to remove existing local-registry container"; exit 1; }
        docker run -d -e REGISTRY_HTTP_ADDR=0.0.0.0:8080 -p 5000:5000 -p 8080:8080 --restart=always --name local-registry registry:2 || { echo "Failed setup local container regsitry"; exit 1; }
        yum install jq -q -y ||  { echo "Failed to install jq package"; exit 1; } && jq -n '{"insecure-registries": $ARGS.positional}' --args "$HOSTNAME:8080" > /etc/docker/daemon.json
        systemctl restart docker || { echo "Docker daemon restart failed"; exit 1; } && systemctl status docker
} 

        export TAG=$VERSION-$BUILD

CREATED_DATE=$(date -u +'%Y-%m-%d %H:%M:%S%:z')

export DOCKER_BUILDKIT=1
export COMPOSE_DOCKER_CLI_BUILD=1
docker-compose -f ./docker-compose.yml build --parallel --force-rm --compress --build-arg GIT_HASH="$GIT_HASH" --build-arg VERSION="$VERSION-$DOCKER_BUILD_BUILD" --build-arg CREATED_DATE="$CREATED_DATE" --build-arg BUILD_URL=$BUILD_URL --build-arg ENVIRONMENT=$ENVIRONMENT --build-arg OS=$OS --build-arg OS_TYPE=$OS_TYPE --build-arg OS_RELEASE=$OS_RELEASE --build-arg CORTX_VERSION="$CORTX_VERSION" $SERVICE


if [ "$REGISTRY" == "local" ]; then
        echo "Setting up local container image registry"
        setup_local_image_registry
        REGISTRY="$HOSTNAME:8080"
fi

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
	docker tag "$(docker images $REGISTRY/$PROJECT/$SERVICE_NAME --format='{{.Repository}}:{{.Tag}}' | head -1)" $REGISTRY/$PROJECT/$SERVICE_NAME:"${TAG//$BUILD/latest}"        
	docker push $REGISTRY/$PROJECT/$SERVICE_NAME:"${TAG//$BUILD/latest}"
else
        echo "Latest tag creation skipped"
fi
done


