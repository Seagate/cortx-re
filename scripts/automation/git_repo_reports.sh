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

Repo_Name=$(echo "$1"|tr "," " ")
GitUserName=$2
file_name=Repository_reports.csv

if [ -f $file_name ]; then
	rm $file_name
fi

git_branch_report(){
	RepoName=$1
	echo "Repo Name: $RepoName"
	git clone --branch main https://github.com/$RepoName.git
	if [ $? -ne 0 ]; then
		echo "ERROR: git clone command getting some error for $RepoName"
		exit 1
	fi
	Repo_folder=$(echo "$RepoName"|rev|cut -d "/" -f1| rev)
	pushd $Repo_folder
	echo "$Repo_folder," >> ../$file_name
	branches_name=$(git for-each-ref --sort=-committerdate --format="%(refname)" refs/remotes)
	if [ $? -ne 0 ]; then
		echo "ERROR: git branch list command getting some error for $RepoName"
		exit 1
	fi
	check_merge_branches=$(git branch -a --no-merged remotes/origin/main)
	if [ $? -ne 0 ]; then
		echo "Check merge branch command failed $RepoName"
		exit 1
	fi
	for branch in $branches_name
	do
		Branch_Name=$(echo "$branch"|cut -c21-)
		if [[ $Branch_Name != "stable" ]] && [[ $Branch_Name != "main" ]] && [[ $Branch_Name != "HEAD" ]]; then
			Branch_Name=$(echo "$branch"|cut -c21-|tr -d ",")
			check_merge_branch_status=$(echo "$check_merge_branches"|grep -w "$Branch_Name"|wc -l)
			if [ $check_merge_branch_status == "1" ]; then
				Branch_Merge="No"
			else
				Branch_Merge="Yes"
			fi
			Timing=$(git log -n1 $branch --pretty=format:"%cr"|tr -d ",")
			check_active_status=$(echo "$Timing"|grep -e months -e year|wc -l)
			if [ $check_active_status == "1" ]; then
				ActiveStatus="No"
			else
				ActiveStatus="Yes"
			fi
			Authoroflast_commit=$(git log -n1 $branch --pretty=format:"%an"|tr -d "<>"|tr -d ",")
			echo ",$Branch_Name,$ActiveStatus,$Branch_Merge,$Timing,$Authoroflast_commit" >> ../$file_name
		fi
	done
	popd -1
}

echo "Repo Name,Branch Name,Active Status,Merge in Main Branch Status,Last Updated,Author Of The Last Commit" > Repository_reports.csv

for RepoName in ${Repo_Name[@]}
do
	RepoName=$GitUserName/$RepoName
	git_branch_report $RepoName $file_name
done
