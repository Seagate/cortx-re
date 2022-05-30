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

#set -eo pipefail

START_BUILD=$1
TARGET_BUILD=$2
BUILD_LOCATION=$3

function usage() {
echo "No inputs provided exiting..."
echo "Please provide start and target build numbers.Script should be executed as.."
echo "$0 START_BUILD TARGET_BUILD"
exit 1
}

if [ $# -eq 0 ]; then
usage
fi

if [ -z "$START_BUILD" ]; then echo "No START_BUILD provided.."; exit 1 ; fi
if [ -z "$TARGET_BUILD" ]; then echo "No TARGET_BUILD provided.."; exit 1; fi

declare -A COMPONENT_LIST=(
[cortx-motr]="https://github.com/Seagate/cortx-motr"
[cortx-hare]="https://github.com/Seagate/cortx-hare"
[cortx-ha]="https://github.com/Seagate/cortx-ha"
[cortx-provisioner]="https://github.com/Seagate/cortx-prvsnr"
[cortx-csm_agent]="https://github.com/Seagate/cortx-manager"
[cortx-py-utils]="https://github.com/Seagate/cortx-utils"
[cortx-rgw-integration]="https://github.com/Seagate/cortx-rgw-integration"
[ceph-base]="https://github.com/Seagate/cortx-rgw"
)

clone_dir="/root/git_build_checkin_stats"
time_zone="Asia/Calcutta"
report_file="../git-build-checkin-report.txt"

test -d $clone_dir/clone && $(rm -rf $clone_dir/clone;mkdir -p $clone_dir/clone) || mkdir -p $clone_dir/clone
export TZ=$time_zone;ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

pushd $clone_dir/clone || exit

if [ -z "$BUILD_LOCATION" ]; then
        CHECK_INPUT_VARIABLE_STATUS=$(echo "$START_BUILD"|grep -c RELEASE.INFO)
        if [ "$CHECK_INPUT_VARIABLE_STATUS" == "0" ]; then
                docker pull "$START_BUILD" || { echo "Failed to pull $START_BUILD"; exit 1; }
                docker run --rm "$START_BUILD" cat /RELEASE.INFO > start_build_manifest.txt
                docker pull "$TARGET_BUILD" || { echo "Failed to pull $TARGET_BUILD"; exit 1; }
                docker run --rm "$TARGET_BUILD" cat /RELEASE.INFO > target_build_manifest.txt
                START_IMAGE=${START_BUILD##*/*:}
                TARGET_IMAGE=${TARGET_BUILD##*/*:}
                START_BUILD=$(grep "BUILD:" start_build_manifest.txt| awk '{print $2}'|tr -d '"')
                TARGET_BUILD=$(grep "BUILD:" target_build_manifest.txt| awk '{print $2}'|tr -d '"')
        else
                wget -q "$START_BUILD" -O start_build_manifest.txt
                if [ $? -ne 0 ]; then
                    echo "ERROR: Downloading RELEASE.INFO file got failed for $START_BUILD"
                    exit 1
                fi
                START_BUILD=$(echo "$START_BUILD"|awk -F "/" '{print $9}')
                wget -q "$TARGET_BUILD" -O target_build_manifest.txt
                if [ $? -ne 0 ]; then
                    echo "ERROR: Downloading RELEASE.INFO file got failed for $TARGET_BUILD"
                    exit 1
                fi
                TARGET_BUILD=$(echo "$TARGET_BUILD"|awk -F "/" '{print $9}')
        fi
else
        wget -q "$BUILD_LOCATION"/"$START_BUILD"/dev/RELEASE.INFO -O start_build_manifest.txt
        wget -q "$BUILD_LOCATION"/"$TARGET_BUILD"/dev/RELEASE.INFO -O target_build_manifest.txt
fi
echo -e "Changelog from $START_IMAGE to $TARGET_IMAGE \n" > $clone_dir/clone/git-build-checkin-report.txt
for component in "${!COMPONENT_LIST[@]}"
do
        echo "Component:$component"
        echo "Repo:${COMPONENT_LIST[$component]}"
         dir=$(echo "${COMPONENT_LIST[$component]}" |  awk -F'/' '{print $NF}')
         git clone -q --branch main "${COMPONENT_LIST[$component]}" "$dir"
         rc=$?
          if [ $rc -ne 0 ]; then
          echo "ERROR:git clone failed for $component"
          exit 1
          fi

                if [ "$component" == "cortx-hare" ] || [ "$component" == "cortx-sspl" ] || [ "$component" == "cortx-ha" ] || [ "$component" == "cortx-py-utils" ]; then
                        start_hash=$(grep "$component-" start_build_manifest.txt | head -1 | awk -F['_'] '{print $2}' | cut -d. -f1 |  sed 's/git//g'); echo "$start_hash"
                        target_hash=$(grep "$component-" target_build_manifest.txt | head -1 | awk -F['_'] '{print $2}' | cut -d. -f1 |  sed 's/git//g'); echo "$target_hash"
                elif [ "$component" == "cortx-csm_agent" ] || [ "$component" == "cortx-csm_web" ]; then
                        start_hash=$(grep "$component-" start_build_manifest.txt | head -1 | awk -F['_'] '{print $3}' |  cut -d. -f1); echo "$start_hash"
                        target_hash=$(grep "$component-" target_build_manifest.txt | head -1 | awk -F['_'] '{print $3}' |  cut -d. -f1); echo "$target_hash"
                elif [ "$component" == "cortx-provisioner" ] || [ "$component" == "cortx-rgw-integration" ] ; then
                        start_hash=$(grep "$component-" start_build_manifest.txt | tail -1 | awk -F['_'] '{print $2}' | sed 's/git//g' | cut -d. -f1); echo "$start_hash"
                        target_hash=$(grep "$component-" target_build_manifest.txt | tail -1 | awk -F['_'] '{print $2}' | sed 's/git//g' | cut -d. -f1); echo "$target_hash"
                elif [ "$component" == "ceph-base" ]; then
                        start_hash=$(grep "$component-" start_build_manifest.txt | awk -F['-'] '{print $5}'  | cut -d. -f2 | sed s/g//g); echo "$start_hash"
                        target_hash=$(grep "$component-" target_build_manifest.txt | awk -F['-'] '{print $5}'  | cut -d. -f2 | sed s/g//g); echo "$target_hash"
                else
                        start_hash=$(grep "$component-" start_build_manifest.txt | head -1 | awk -F['_'] '{print $2}' | sed 's/git//g'|cut -d. -f1); echo "$start_hash"
                        target_hash=$(grep "$component-" target_build_manifest.txt | head -1 | awk -F['_'] '{print $2}' | sed 's/git//g'|cut -d. -f1); echo "$target_hash"
                fi

                 pushd "$dir" || exit
                        change="$(git log "$start_hash..$target_hash" --oneline --pretty=format:"%s")";
                if [ "$change" ]; then
                        echo "$dir" >> $report_file
                        echo -e  "$change \n" >> $report_file
                        GITHUB_URL="${COMPONENT_LIST[$component]}"
                        sed -i -e s/\(#/"${GITHUB_URL//\//\\/}\/pull\/"/g -e s/\)//g  $report_file >> $report_file
                fi
         popd || exit

done
popd || exit

echo -e "---------------------------------------------------------------------------------------------"
echo -e "----------------------------------[ Printing report ]----------------------------------------"
cat $clone_dir/clone/git-build-checkin-report.txt
