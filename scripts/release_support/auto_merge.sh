#!/bin/bash

function usage() {
echo "No inputs provided exiting..."
echo "Please provide GitHub Token, source branch and destination branch.Script should be executed as.."
echo "$0 <GITHUB TOKEN> dev relase"
exit 1
}

if [ $# -eq 0 ]; then
usage
fi

GIT_CRED=$1
SOURCE_BRANCH=$2
DEST_BRANCH=$3

git config --global user.email "cortx-application@seagate.com"
git config --global user.name "cortx-admin"


#declare -A COMPONENT_LIST=(
#[cortx-s3server]="https://$GIT_CRED@github.com/Seagate/cortx-s3server.git"
#[cortx-motr]="https://$GIT_CRED@github.com/Seagate/cortx-motr.git"
#[cortx-hare]="http://gitlab.mero.colo.seagate.com/mero/hare.git"
#[cortx-ha]="https://$GIT_CRED@github.com/Seagate/cortx-ha.git"
#[cortx-prvsnr]="https://$GIT_CRED@github.com/Seagate/cortx-prvsnr.git"
#[cortx-sspl]="https://$GIT_CRED@github.com/Seagate/cortx-sspl.git"
#[cortx-csm_agent]="https://$GIT_CRED@github.com/Seagate/cortx-csm.git"
#[cortx-fs]="https://$GIT_CRED@github.com/Seagate/cortx-posix.git"
#)

declare -A COMPONENT_LIST=(
[cortx-re]="https://$GIT_CRED@github.com/shailesh-vaidya/cortx-re.git"
)

clone_dir="/root/git_auto_merge"
time_zone="Asia/Calcutta"

test -d $clone_dir/clone && $(rm -rf $clone_dir/clone;mkdir -p $clone_dir/clone) || mkdir -p $clone_dir/clone

pushd $clone_dir/clone

for component in "${!COMPONENT_LIST[@]}"
do
        echo "Component:$component"
        echo "Repo:${COMPONENT_LIST[$component]}"
         dir=$(echo ${COMPONENT_LIST[$component]} |  awk -F'/' '{print $NF}')
         git clone --branch dev ${COMPONENT_LIST[$component]} $dir
         rc=$?
          if [ $rc -ne 0 ]; then
          echo "ERROR:git clone failed for $component"
          exit 1
          fi
         pushd $dir
         echo -e "\t--[ Automated merge for $dir ]--"
         git checkout $DEST_BRANCH && git merge $SOURCE_BRANCH -m "Automated merge from dev to release at $(date +"%d-%b-%Y %H:%M")" && git push origin release
         popd

done
popd
