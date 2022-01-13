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

declare -A COMPONENT_LIST=(
                        [cortx-s3server]="https://$PASSWD@github.com/Seagate/cortx-s3server.git"
                        [cortx-motr]="https://$PASSWD@github.com/Seagate/cortx-motr.git"
                        [cortx-hare]="https://$PASSWD@github.com/Seagate/cortx-hare.git"
                        [cortx-ha]="https://$PASSWD@github.com/Seagate/cortx-ha.git"
                        [cortx-prvsnr]="https://$PASSWD@github.com/Seagate/cortx-prvsnr.git"
                        [cortx-sspl]="https://$PASSWD@github.com/Seagate/cortx-monitor.git"
                        [cortx-csm_agent]="https://$PASSWD@github.com/Seagate/cortx-manager.git"
                        [cortx-csm_web]="https://$PASSWD@github.com/Seagate/cortx-management-portal.git"
                        [cortx-py-utils]="https://$PASSWD@github.com/Seagate/cortx-utils.git"
                        [cortx-prereq]="https://$PASSWD@github.com/Seagate/cortx-re.git"
                )

        git config --global user.email "cortx-application@seagate.com"
        git config --global user.name "cortx-admin"
        wget -q $RELEASE_INFO_URL -O RELEASE.INFO

        for component in "${!COMPONENT_LIST[@]}"

        do
                dir="$(echo "${COMPONENT_LIST[$component]}" |  awk -F'/' '{print $NF}')"
                git clone --quiet "${COMPONENT_LIST["$component"]}" "$dir" > /dev/null

                rc=$?
                if [ "$rc" -ne 0 ]; then
                        echo "ERROR:git clone failed for "$component""
                exit 1
                fi

                if [ $component == "cortx-hare" ] || [ $component == "cortx-sspl" ] || [ $component == "cortx-ha" ] || [ $component == "cortx-py-utils" ] || [ "$component" == "cortx-prereq" ]; then
                        COMMIT_HASH=$(grep "$component-" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | cut -d. -f1 |  sed 's/git//g'); echo $COMMIT_HASH          
                elif [ "$component" == "cortx-csm_agent" ] || [ "$component" == "cortx-csm_web" ]; then
                        COMMIT_HASH=$(grep "$component-" RELEASE.INFO | head -1 | awk -F['_'] '{print $3}' |  cut -d. -f1); echo "$COMMIT_HASH"

                elif [ "$component" == "cortx-prvsnr" ]; then
                        COMMIT_HASH=$(grep "$component-" RELEASE.INFO | tail -1 | awk -F['_'] '{print $2}' | sed 's/git//g' | cut -d. -f1); echo $COMMIT_HASH

                else
                        COMMIT_HASH=$(grep "$component-" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | sed 's/git//g'); echo $COMMIT_HASH

                fi
                echo "Component2: "$component" , Repo:  "${COMPONENT_LIST[$component]}", Commit Hash: "$COMMIT_HASH""
                pushd "$dir"
                if [ "$GIT_TAG" != "" ]; then
                        git tag -a "$GIT_TAG" "$COMMIT_HASH" -m "$TAG_MESSAGE";
                        git push origin "$GIT_TAG";
                        echo "Component: $component , Tag: git tag -l $GIT_TAG is Tagged Successfully";
                        git tag -l "$GIT_TAG";
                else
                        echo "Tag is not successful. Please pass value to GIT_TAG";
                fi

                if [ "$DEBUG" = true ]; then
                        git push origin --delete "$GIT_TAG";
                 else
                        echo "Run in Debug mode if Git tag needs to be deleted";

                fi

                popd
        done

