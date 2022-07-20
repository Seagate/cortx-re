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

# export CORTX_IMAGE, GIT_TAG and TAG_MESSAGE variables with appropriate values
# CORTX_IMAGE - Any CORTX service image as input. Reference: https://github.com/Seagate/cortx-re/pkgs/container/cortx-rgw
# GIT_TAG - Tag to be tagged to commits
# TAG_MESSAGE - Tag message with tag

function get_commit_hash() {
    component="$1"
    for commit_detail in ${component_commit_details//,/ }
    do
        component_name=$(awk -F":" '{print $1}' <<< "$commit_detail")
        if [ "$component_name" == "$component" ]; then
            commit=$(awk -F":" '{print $2}' <<< "$commit_detail")
            echo "$commit"
        fi
    done
}

if [ -z "$CORTX_IMAGE" ]; then 
    echo "CORTX_IMAGE is not provided. exiting..."
    exit 1
else
    echo "Pulling $CORTX_IMAGE"
    docker pull "$CORTX_IMAGE" || { echo "Failed to pull image $CORTX_IMAGE"; exit 1; }
fi

component_commit_details=$(docker inspect -f '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$CORTX_IMAGE")
declare -A COMPONENT_LIST=(
        [cortx-motr]="https://github.com/Seagate/cortx-motr.git"
        [cortx-hare]="https://github.com/Seagate/cortx-hare.git"
        [cortx-ha]="https://github.com/Seagate/cortx-ha.git"
        [cortx-provisioner]="https://github.com/Seagate/cortx-prvsnr.git"
        [cortx-csm_agent]="https://github.com/Seagate/cortx-manager.git"
        [cortx-py-utils]="https://github.com/Seagate/cortx-utils.git"
        [cortx-rgw-integration]="https://github.com/Seagate/cortx-rgw-integration.git"
        [cortx-rgw]="https://github.com/Seagate/cortx-rgw.git"
        [cortx-re]="https://github.com/Seagate/cortx-re.git"
)
git config --global user.email "cortx-application@seagate.com"
git config --global user.name "cortx-admin"

for component in "${!COMPONENT_LIST[@]}"
do
    dir="$(echo "${COMPONENT_LIST[$component]}" |  awk -F'/' '{print $NF}')"
    git clone --quiet "${COMPONENT_LIST["$component"]}" "$dir" > /dev/null
    rc=$?
    if [ "$rc" -ne 0 ]; then
        echo "ERROR:git clone failed for "$component""
        exit 1
    fi
    COMMIT_HASH=$(get_commit_hash "$component")
    echo "Component: "$component" , Repo:  "${COMPONENT_LIST[$component]}", Commit Hash: "$COMMIT_HASH""
    pushd "$dir"
        if [ ! -z "$GIT_TAG" ]; then
            git tag -a "$GIT_TAG" "$COMMIT_HASH" -m "$TAG_MESSAGE" || { echo "Failed to tag commit $COMMIT_HASH with tag $GIT_TAG"; exit 1; }
            git push origin "$GIT_TAG" || { echo "Failed to push tag $GIT_TAG for commit $COMMIT_HASH"; exit 1; }
            echo "$component commit $COMMIT_HASH has tagged successfully with tag $GIT_TAG";
            git tag -l "$GIT_TAG";
        else
            echo "GIT_TAG value is not provided"
            exit 1
        fi
    popd
done