#!/bin/bash

PRE="true"
REGISTRY="ghcr.io/seagate"
RELEASE_REPO_NAME="cortx-re"
RELEASE_REPO_OWNER="gauravchaudhari02"
BUILD_INFO="**Build Instructions**: https://github.com/Seagate/cortx-re/tree/main/solutions/community-deploy/cloud/AWS#cortx-build"
DEPLOY_INFO="**Deployment Instructions**: https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/CORTX-Deployment.md"
SERVICES_VERSION_TITLE="**CORTX Services Release**: "
CORTX_IMAGE_TITLE="**CORTX Container Images**: "
CHANGESET_TITLE="**Changeset**: "
# get args
while getopts :t:v:c: option
do
        case "${option}"
                in
                t) TAG="$OPTARG";;
                v) SERVICES_VERSION="$OPTARG";;
                c) CHANGESET_URL="$OPTARG";;
        esac
done
if [[ -z "$TAG" || -z "$SERVICES_VERSION" || -z "$CHANGESET_URL" ]]; then
        echo "Usage: git-release [-t <tag>] [-v <services-version>] [-c <changeset file url>]"
        exit 1
fi

# Install required packages
yum install -y https://github.com/cli/cli/releases/download/v2.14.3/gh_2.14.3_linux_amd64.rpm || { echo "ERROR: failed to install gh"; exit 1; } 
yum install jq -y || { echo "ERROR: failed to install jq"; exit 1; } 
# set release content
CHANGESET=$( curl -ks $CHANGESET_URL | tail -n +2 )
CORTX_SERVER_IMAGE="[$REGISTRY/cortx-rgw:$TAG]($REGISTRY/cortx-rgw:$TAG)"
CORTX_DATA_IMAGE="[$REGISTRY/cortx-data:$TAG]($REGISTRY/cortx-data:$TAG)"
CORTX_CONTROL_IMAGE="[$REGISTRY/cortx-control:$TAG]($REGISTRY/cortx-control:$TAG)"
IMAGES_INFO="| Image      | Location |\n|    :----:   |    :----:   |\n| cortx-server      | $CORTX_SERVER_IMAGE       |\n| cortx-data      | $CORTX_DATA_IMAGE      |\n| cortx-control      | $CORTX_CONTROL_IMAGE       |"
SERVICES_VERSION="[$SERVICES_VERSION](https://github.com/Seagate/cortx-k8s/releases/tag/$SERVICES_VERSION)"
MESSAGE="$CORTX_IMAGE_TITLE\n$IMAGES_INFO\n\n$SERVICES_VERSION_TITLE$SERVICES_VERSION\n\n$BUILD_INFO\n$DEPLOY_INFO\n\n$CHANGESET_TITLE\n\n${CHANGESET//$'\n'/\\n}"

API_JSON=$(printf '{"tag_name":"%s","name":"%s","body":"%s","prerelease":%s}' "$TAG" "$TAG" "$MESSAGE" "$PRE" )
# echo $API_JSON
# if jq -n $API_JSON | gh api /repos/$RELEASE_REPO_OWNER/$RELEASE_REPO_NAME/releases --input - > /tmp/api_response.html
# gh release create test-tag --repo gauravchaudhari02/cortx-re --notes-file CHANGESET.md -p
if curl --data "$API_JSON" -sif -H "Accept: application/json" -H "Authorization: token $GH_TOKEN" "https://api.github.com/repos/$RELEASE_REPO_OWNER/$RELEASE_REPO_NAME/releases" > /tmp/api_response.html
then
    echo "https://github.com/$RELEASE_REPO_OWNER/$RELEASE_REPO_NAME/releases/tag/$TAG"
else
    echo "ERROR: curl command has failed. Please check API response for more details"
    exit 1
fi      