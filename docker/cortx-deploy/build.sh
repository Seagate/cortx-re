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

usage() { echo "Usage: $0 [-b build url]" 1>&2; exit 1; }

while getopts ":b:" o; do
    case "${o}" in
        b)
            BUILD_URL=${OPTARG}
            ;;
        *)
            usage
            ;;
    esac
done
shift $((OPTIND-1))

if [ -z "${BUILD_URL}" ] ; then
    usage
fi

echo $BUILD_URL

curl $BUILD_URL/RELEASE.INFO -o RELEASE.INFO

for i in BRANCH BUILD
do
export DOCKER_BUILD_$i=$(grep $i RELEASE.INFO | cut -d'"' -f2)
done
rm -rf RELEASE.INFO

pushd ../.././
export TAG=$DOCKER_BUILD_BRANCH-$DOCKER_BUILD_BUILD
docker-compose -f docker/cortx-deploy/docker-compose.yml build --force-rm  --compress --build-arg GIT_HASH="$(git rev-parse --short HEAD)" --build-arg BUILD_URL=$BUILD_URL  cortx-all
docker-compose -f docker/cortx-deploy/docker-compose.yml push
popd
