#!/bin/bash
#
# Copyright (c) 2021 Seagate Technology LLC and/or its Affiliates
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

SOURCE_BRANCH="$1"
TARGET_BRANCH="$2"
clone_dir="/root/git_branch_diff"
time_zone="Asia/Calcutta"
report_file="${clone_dir}/git-branch-diff-report.txt"

declare -A COMPONENT_LIST=( 
[cortx-s3server]="https://github.com/Seagate/cortx-s3server.git"
[cortx-motr]="https://github.com/Seagate/cortx-motr.git"
[cortx-hare]="https://github.com/Seagate/cortx-hare.git"
[cortx-ha]="https://github.com/Seagate/cortx-ha.git"
[cortx-prvsnr]="https://github.com/Seagate/cortx-prvsnr.git"
[cortx-sspl]="https://github.com/Seagate/cortx-sspl.git"
[cortx-manager]="https://github.com/Seagate/cortx-manager.git"
[cortx-management-portal]="https://github.com/Seagate/cortx-management-portal.git"
[cortx-utils]="https://github.com/Seagate/cortx-utils.git"
)

function usage() {
    cat << HEREDOC
Usage : $0 SOURCE_BRANCH TARGET_BRANCH
HEREDOC
}

if [ $# -lt 2 ]; then
    echo "ERROR: Provided incorrect inputs"
    usage
fi

test -d $clone_dir/clone && $(rm -rf $clone_dir/clone;mkdir -p $clone_dir/clone) || mkdir -p $clone_dir/clone
export TZ=$time_zone;ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

pushd $clone_dir/clone

for component in "${!COMPONENT_LIST[@]}"
do
        echo "Component:$component"
        echo "Repo:${COMPONENT_LIST[$component]}"
        git clone --branch main ${COMPONENT_LIST[$component]}
        if [ $? -ne 0 ]; then 
            echo "ERROR:git clone failed for $component"
            exit 1
        fi
        pushd $component
            git checkout $SOURCE_BRANCH
            if [ $? -ne 0 ]; then 
                echo "INFO: Branch $SOURCE_BRANCH is not available"
                continue
            fi
            echo -e "\033[1m$SOURCE_BRANCH Branch Delta\033[0m" >> $report_file
            echo -e "--------------------------" >> $report_file
            echo -e "---[ \033[1m$component\033[0m ]---" >> $report_file
            echo -e "--------------------------\n" >> $report_file
			echo -e "\t--[ Commit differnce for $component from $SOURCE_BRANCH to $TARGET_BRANCH ]--" >> $report_file
			echo -e "Githash|Description|Author|" >> $report_file
			sourcechanges="$(git log "$SOURCE_BRANCH..$TARGET_BRANCH" --oneline --pretty=format:"%h|%cd|%s|%an|")";
		    if [ "$sourcechanges" ]; then
			    echo "$sourcechanges" >> $report_file
                echo -e "\n\n" >> $report_file
			else
			    echo "No Changes" >> $report_file
			    echo -e "\n" >> $report_file
            fi
            echo -e "\033[1m$TARGET_BRANCH Branch Delta\033[0m" >> $report_file
            echo -e "\t--[ Commit differnce for $component from $TARGET_BRANCH to $SOURCE_BRANCH ]--" >> $report_file
            echo -e "Githash|Description|Author|" >> $report_file
			targetchanges="$(git log "$TARGET_BRANCH..$SOURCE_BRANCH" --oneline --pretty=format:"%h|%cd|%s|%an|")";
		    if [ "$targetchanges" ]; then
			    echo "$targetchanges" >> $report_file
                echo -e "\n\n" >> $report_file
			else
			    echo "No Changes" >> $report_file
			    echo -e "\n\n" >> $report_file
            fi

        popd
done