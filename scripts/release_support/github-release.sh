#!/bin/bash

PRE="true"
REGISTRY="ghcr.io/seagate"
RELEASE_REPO_NAME="cortx-re"
RELEASE_REPO_OWNER="gauravchaudhari02"
BUILD_INFO_STRING="**Build Instructions**: https://github.com/Seagate/cortx-re/tree/main/solutions/community-deploy/cloud/AWS#cortx-build\n"
DEPLOY_INFO_STRING="**Deployment Instructions**: https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/CORTX-Deployment.md\n"
SERVICES_VERSION_STRING="**CORTX Services Release**: "
CORTX_IMAGE_STRING="**CORTX Component Images: "
CHANGESET_STRING="**Changeset**: "
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
echo $TAG $SERVICES_VERSION $CHANGESET_URL
if [[ -z "$TAG" || -z "$SERVICES_VERSION" || -z "$CHANGESET_URL" ]]; then
        echo "Usage: git-release [-t <tag>] [-v <services-version>] [-c <changeset file url>]"
        exit 1
fi

# set release content
CHANGESET=$( curl -ks $CHANGESET_URL | tail -n +2 )
CORTX_SERVER_IMAGE="[$REGISTRY/cortx-rgw:$TAG]($REGISTRY/cortx-rgw:$TAG)"
CORTX_DATA_IMAGE="[$REGISTRY/cortx-data:$TAG]($REGISTRY/cortx-data:$TAG)"
CORTX_CONTROL_IMAGE="[$REGISTRY/cortx-control:$TAG]($REGISTRY/cortx-control:$TAG)"
MESSAGE="$CORTX_IMAGE_STRING\n$CORTX_SERVER_IMAGE\n$CORTX_DATA_IMAGE\n$CORTX_CONTROL_IMAGE\n\n$SERVICES_VERSION_STRING$SERVICES_VERSION\n\n$BUILD_INFO_STRING$DEPLOY_INFO_STRING\n\n$CHANGESET_STRING\n\n${CHANGESET//$'\n'/\\n}"


API_JSON=$(printf '{"tag_name": "%s","name": "%s","body": "%s","prerelease": %s}' "$TAG" "$TAG" "$MESSAGE" "$PRE" )
API_RESPONSE_STATUS=$(curl --data "$API_JSON" -s -i -H "Accept: application/vnd.github+json" -H "Authorization: token $GITHUB_ACCESS_TOKEN" https://api.github.com/repos/$RELEASE_REPO_OWNER/$RELEASE_REPO_NAME/releases)
echo "$API_RESPONSE_STATUS"