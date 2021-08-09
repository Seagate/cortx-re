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

BUILD=$1
BRANCH=$2
OS=$3

mkdir -p /mnt/data1/releases
mount="cortx-storage.colo.seagate.com:/mnt/data1/releases"
echo $mount;
grep -qs "$mount" /proc/mounts;
if grep -qs "$mount" /proc/mounts; then
        echo "cortx-storage.colo.seagate.com:/mnt/data1/releases is mounted."
else
        echo "cortx-storage.colo.seagate.com:/mnt/data1/releases is not mounted."
        sudo mount -t nfs4 "$mount" /mnt/data1/releases
        if [ $? -eq 0 ]; then
                echo "Mount success!"
        else
                echo "Something went wrong with the mount..."
                currentBuild.result = 'ABORTED'
        fi
fi
CURRENT=$(df -h | grep /mnt/data1/releases | awk '{print $5}' | sed 's/%//g')
THRESHOLD=75
echo "The Current disk space is $CURRENT "
if [ "$CURRENT" -gt "$THRESHOLD" ] ; then
        echo Your /mnt/data1/releases partition remaining free space is critically low. Used: $CURRENT%. Threshold: $THRESHOLD%  So, 30 days older files will be deleted $(date)
        #fpath=/mnt/data1/releases
        #source /var/lib/jenkins/workspace/Alert/exclude_build.txt

        echo ----Backup of exclude builds--------
        build=($(echo $BUILD | sed -e 's/,/ /g' -e 's/"//g'))
        #paths=$(echo $PATHS | sed -e 's/,/ /g' -e 's/"//g')
		branch=($(echo $BRANCH | sed -e 's/,/ /g' -e 's/"//g'))
		os=($(echo $OS | sed -e 's/,/ /g' -e 's/"//g'))
        #echo $paths
		echo $build
		echo $branch
		echo $os
	for i in ${!branch[@]}; do
        #for ((i=0; i<"${#branch[@]}"; i++)) do
	for j in ${!os[@]}; do
	for k in ${!build[@]}; do
		fpath=/mnt/data1/releases/cortx/github/${branch[i]}/${os[j]}
                echo $fpath
                find $fpath/${build[k]} -path $fpath -prune -false -o -name '*' -exec cp -R {} /mnt/data1/releases/backups/cortx_build_backup/ \;
                ls -lrt /mnt/data1/releases/backups/cortx_build_backup/ | grep -w 531
                ls -lrt /mnt/data1/releases/backups/cortx_build_backup/ | grep -w 561
                ls -lrt /mnt/data1/releases/backups/cortx_build_backup/ | grep -w 2750
        done
        #find $build -path $PATH1 -path $PATH2 -prune -false -o -name '*' -exec cp -R {} /mnt/data1/releases/backups/cortx_build_backup/ \;
	done
	done
        if [ "$BRANCH" == "main" ] ; then
                echo -----Files to be Deleted from MAIN branch-----
                fpath=/mnt/data1/releases/cortx/github/${BRANCH}/${OS}
                #find $fpath -type f -mtime +20 ! -name '*.INFO*' -exec ls -lrt {} + > $WORKSPACE/file1.out;
                #find $fpath -type f -mtime +20 ! -name '*.INFO*' -exec rm -rf {} \;
        else
                echo -----Files to be Deleted from STABLE branch-----
                fpath=/mnt/data1/releases/cortx/github/stable/${OS}
                #find $fpath -type f -mtime +20 ! -name '*.INFO*' -exec ls -lrt {} + > $WORKSPACE/file1.out;
                #find $fpath -type f -mtime +20 ! -name '*.INFO*' -exec rm -rf {} \;
        fi
fi

