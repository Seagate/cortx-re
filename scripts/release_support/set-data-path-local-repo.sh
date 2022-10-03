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
#!/bin/bash
REPO=${1:-seagate/cortx-motr}
RELEASE=${2}

RELEASE_REPO_LOCATION="/var/tmp/$RELEASE"
RELEASE_REPO_FILE="$RELEASE.repo"


function _create_local_repo () {
        REPO_LOCATION=$1
        DOWNLOAD_RELEASE=$2
        rm -rf $REPO_LOCATION
        mkdir $REPO_LOCATION && pushd $REPO_LOCATION
        githubrelease asset $REPO download $DOWNLOAD_RELEASE && /bin/createrepo -v . || exit
        popd
}

function _create_local_repo_file () {
rm -rf $RELEASE_REPO_FILE
cat << EOF >> $RELEASE_REPO_FILE
[releases_$RELEASE]
name=Cortx $RELEASE  Repository
baseurl=file://$RELEASE_REPO_LOCATION
gpgcheck=0
enabled=1
EOF
}

function _setup_local_repo () {
cp -f $RELEASE_REPO_FILE /etc/yum.repos.d/
yum clean all; rm -rf /var/cache/yum
}


_create_local_repo $RELEASE_REPO_LOCATION $DOWNLOAD_RELEASE
#_create_local_repo $UPLOADS_REPO_LOCATION $UPLOADS
_create_local_repo_file
_setup_local_repo
