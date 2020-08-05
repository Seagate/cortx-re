#!/bin/bash

function usage() {
echo "No inputs provided exiting..."
echo "Please provide GitHub Token, repo url,source branch and destination branch.Script should be executed as.."
echo "$0 <GITHUB TOKEN> https://github.com/Seagate/cortx-re dev relase"
exit 1
}

if [ $# -eq 0 ]; then
usage
fi

TOKEN=$1
REPO_URL=$2
SOURCE_BRANCH=$3
DEST_BRANCH=$4

git config --global user.email "cortx-application@seagate.com"
git config --global user.name "cortx-admin"

REPO_CLONE_URL=$(echo $REPO_URL | sed 's/github/'$TOKEN'@github/g')

clone_dir="/root/git_auto_merge"
test -d $clone_dir/clone && $(rm -rf $clone_dir/clone;mkdir -p $clone_dir/clone) || mkdir -p $clone_dir/clone

pushd $clone_dir/clone

         dir=$(echo $REPO_CLONE_URL |  awk -F'/' '{print $NF}')
         git clone --branch dev $REPO_CLONE_URL  $dir
         rc=$?
          if [ $rc -ne 0 ]; then
          echo "ERROR:git clone failed for $component"
          exit 1
          fi
         pushd $dir
         echo -e "\t--[ Automated merge for $dir ]--"
         git checkout $DEST_BRANCH && git merge $SOURCE_BRANCH -m "Automated merge from dev to release at $(date +"%d-%b-%Y %H:%M")" && git push origin release
         popd

popd
