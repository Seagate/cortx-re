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

PRE="true"
REGISTRY="ghcr.io/seagate"
BUILD_INFO="**Build Instructions**: https://github.com/Seagate/cortx-re/tree/main/solutions/community-deploy/cloud/AWS#cortx-build"
DEPLOY_INFO="**Deployment Instructions**: https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/CORTX-Deployment.md"
SERVICES_VERSION_TITLE="**CORTX Services Release**: "
CORTX_IMAGE_TITLE="**CORTX Container Images**: "
CHANGESET_TITLE="**Changeset**: "
# get args
while getopts :t:v:c:r: option
do
        case "${option}"
                in
                t) TAG="$OPTARG";;
                v) SERVICES_VERSION="$OPTARG";;
                c) CHANGESET_URL="$OPTARG";;
                r) RELEASE_REPO="$OPTARG";;
        esac
done
if [[ -z "$TAG" || -z "$SERVICES_VERSION" || -z "$CHANGESET_URL" ]]; then
        echo "Usage: git-release [-t <tag>] [-v <services-version>] [-c <changeset file url>]"
        exit 1
fi

# function to fetch container id of latest container image
function get_container_id {
    cortx_container="$1"
    container_id=$(curl -s -H "Accept: application/vnd.github+json" -H "Authorization: token $GH_TOKEN" https://api.github.com/orgs/seagate/packages/container/$cortx_container/versions | jq ".[] | select(.metadata.container.tags[]==\"$TAG\") | .id")
    echo "$container_id"
}

function getImageTags {
    image="$1"
    tag="$2"
    tags=$( curl -s -H "Accept: application/vnd.github+json" -H "Authorization: token ${GH_TOKEN}" "https://api.github.com/orgs/seagate/packages/container/${image}/versions" | jq ".[] | select(.metadata.container.tags[]==\"${tag}\") | .metadata.container.tags[]" | awk '!/2.0.0-latest/' | tr -d '"' )
    echo ${tags} | tr ' ' '/'
}

# Fetch tags of given image
image_tags=$(getImageTags "cortx-rgw" "$TAG")

# Set release content
CHANGESET=$( curl -ks $CHANGESET_URL | tail -n +2 | sed 's/"//g' )
CORTX_SERVER_IMAGE="[$REGISTRY/cortx-rgw:$TAG](https://github.com/$RELEASE_REPO/pkgs/container/cortx-rgw/$( get_container_id "cortx-rgw" )?tag=$TAG)"
CORTX_DATA_IMAGE="[$REGISTRY/cortx-data:$TAG](https://github.com/$RELEASE_REPO/pkgs/container/cortx-data/$( get_container_id "cortx-data" )?tag=$TAG)"
CORTX_CONTROL_IMAGE="[$REGISTRY/cortx-control:$TAG](https://github.com/$RELEASE_REPO/pkgs/container/cortx-control/$( get_container_id "cortx-control" )?tag=$TAG)"
IMAGES_INFO="| Image      | Location |\n|    :----:   |    :----:   |\n| cortx-server      | $CORTX_SERVER_IMAGE       |\n| cortx-data      | $CORTX_DATA_IMAGE      |\n| cortx-control      | $CORTX_CONTROL_IMAGE       |"
SERVICES_VERSION="[$SERVICES_VERSION](https://github.com/Seagate/cortx-k8s/releases/tag/$SERVICES_VERSION)"
MESSAGE="$CORTX_IMAGE_TITLE\n$IMAGES_INFO\n\n$SERVICES_VERSION_TITLE$SERVICES_VERSION\n\n$BUILD_INFO\n$DEPLOY_INFO\n\n$CHANGESET_TITLE\n\n${CHANGESET//$'\n'/\\n}"

API_JSON=$(printf '{"tag_name":"%s","name":"%s","body":"%s","prerelease":%s}' "$TAG" "$TAG" "$MESSAGE" "$PRE" )
if curl --data "$API_JSON" -sif -H "Accept: application/json" -H "Authorization: token $GH_TOKEN" "https://api.github.com/repos/$RELEASE_REPO/releases" > api_response.html
then
    echo "https://github.com/$RELEASE_REPO/releases/tag/$TAG $image_tags"
else
    echo "ERROR: curl command has failed. Please check API response for more details"
    exit 1
fi