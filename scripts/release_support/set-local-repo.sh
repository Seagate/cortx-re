#
# Copyright (c) 2022 Seagate Technology LLC and/or its Affiliates
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
set -euf -o pipefail

#Install required packages
yum install libmodulemd -y
for package in epel-release jq python3-pip createrepo yum-utils; do rpm -q $package || yum install $package -y; done
export LC_ALL=C.UTF-8 && export LANG=C.UTF-8
yum-config-manager --set-enabled powertools

pip3 show githubrelease || (pip3 install click==7.1.2 && pip3 install githubrelease==1.5.8)

REPO=${1:-seagate/cortx-motr}
REPO_LATEST_RELEASE=$(curl -s https://api.github.com/repos/"$REPO"/releases/latest  | jq ".. .tag_name? // empty" | tr -d '"')
RELEASE=${2:-$REPO_LATEST_RELEASE}
RELEASE_REPO_LOCATION="/var/tmp/$RELEASE"
RELEASE_REPO_FILE="/etc/yum.repos.d/$(echo "$RELEASE_REPO_LOCATION" | sed -e 's/\///' -e 's/\//_/g').repo"

#Download packages from GitHub Release
echo -e "\n################################################################################"
echo -e "### REPO=$REPO"
echo -e "### RELEASE=$RELEASE"
echo -e "################################################################################\n"
rm -rf "$RELEASE_REPO_LOCATION" && mkdir "$RELEASE_REPO_LOCATION" || exit
pushd "$RELEASE_REPO_LOCATION" || exit
        githubrelease asset "$REPO" download "$RELEASE" && /bin/createrepo -v . || exit
popd || exit

#Setup yum repository
rm -f $RELEASE_REPO_FILE
yum-config-manager --add-repo file://"$RELEASE_REPO_LOCATION"
echo "gpgcheck=0" >> "$RELEASE_REPO_FILE"
yum clean all; rm -rf /var/cache/yum
