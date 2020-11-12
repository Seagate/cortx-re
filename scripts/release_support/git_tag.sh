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
set +x

while [[ "$#" -gt 0 ]]; do
    case $1 in
        -t|--tag) GIT_TAG="$2"; shift ;;
        -r|--release) RELEASE_INFO="$2"; shift ;;
        -m|--message) TAG_DESCRIPTION="$2"; shift ;;
        -x|--cred) GIT_CRED="$2"; shift ;;
        *) echo "Unknown parameter passed: $1"; exit 1 ;;
    esac
    shift
done

GITHUB_ORG="gowthamchinna"
#GITHUB_ORG="Seagate"

declare -A COMPONENT_LIST=( 
    [cortx-s3server]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-s3server.git"
    [cortx-motr]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-motr.git"
    [cortx-hare]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-hare.git"
    [cortx-ha]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-ha.git"
    [cortx-prvsnr]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-prvsnr.git"
    [cortx-sspl]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-monitor.git"
    [cortx-csm_agent]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-manager.git"
    [cortx-csm_web]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-management-portal.git"
    #[cortx-fs]="https://$GIT_CRED@github.com/$GITHUB_ORG/cortx-posix.git"
)

git config --global user.email "cortx-re@seagate.com"
git config --global user.name "cortx-re"

wget -q "$RELEASE_INFO" -O RELEASE.INFO

for component in "${!COMPONENT_LIST[@]}"
do
    dir=$(echo "${COMPONENT_LIST[$component]}" |  awk -F'/' '{print $NF}')
    git clone --quiet "${COMPONENT_LIST[$component]}" "$dir" > /dev/null

    rc=$?
    if [ $rc -ne 0 ]; then 
        echo "ERROR:git clone failed for $component"
        exit 1
    fi

    if [ "$component" == cortx-hare ] || [ "$component" == cortx-sspl ] || [ "$component" == cortx-ha ] || [ "$component" == cortx-fs ]; then
        COMMIT_HASH=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | cut -d. -f1 |  sed 's/git//g');
    elif [ "$component" == "cortx-csm_agent" ] || [ "$component" == "cortx-csm_web" ]; then
        COMMIT_HASH=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $3}' |  cut -d. -f1);
    else
        COMMIT_HASH=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | sed 's/git//g');
    fi

    echo "Component: $component , Repo:  ${COMPONENT_LIST[$component]}, Commit Hash: ${COMMIT_HASH}"
    pushd "$dir"
        git tag "$GIT_TAG" "${COMMIT_HASH}" -am "${TAG_DESCRIPTION}"
        git push origin "$GIT_TAG"
    popd
	
done