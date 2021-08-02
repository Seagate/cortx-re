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

usage() { echo "Usage: $0 [-l build location] [-b branch] [-v version] [ -t third party location]" 1>&2; exit 1; }

while getopts ":l:v:t:b:" o; do
    case "${o}" in
        l)
            BUILD_LOCATION=${OPTARG}
            ;;
        t)
            THIRD_PARTY_LOCATION=${OPTARG}
            ;;    
        v)
            VERSION=${OPTARG}
            ;;
        b)
            BRANCH=${OPTARG}
            ;;
        *)
            usage
            ;;
    esac
done
shift $((OPTIND-1))

if [ -z "${BUILD_LOCATION}" ] || [ -z "${BRANCH}" ] || [ -z "${VERSION}" ] || [ -z "${THIRD_PARTY_LOCATION}" ]; then
    usage
fi

if [ -z "${BUILD_NUMBER}" ]; then
BUILD_NUMBER="0"
fi

echo -e "Generating RELEASE.INFO file"
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
EXCLUDE_PACKAGES="cortx-motr-devel\|cortx-motr-tests-ut\|cortx-libsspl_sec-devel\|cortx-libsspl_sec-method_pki\|cortx-prvsnr-cli\|cortx-sspl-cli\|cortx-s3iamcli-devel\|cortx-sspl-test"

create_metadata() {
FILE_NAME=$1
cat <<EOF > "$FILE_NAME"
---
NAME: "CORTX"
VERSION: "$VERSION"
BRANCH: "$BRANCH"
THIRD_PARTY_VERSION: $(readlink -f "$THIRD_PARTY_LOCATION" | awk -F '/' '{print $NF}' | sed -e 's/^/\"/g' -e 's/$/\"/g')
BUILD: $(echo "$BUILD_NUMBER" | sed -e 's/^/\"/g' -e 's/$/\"/g')
OS: $(cat /etc/redhat-release | sed -e 's/ $//g' -e 's/^/\"/g' -e 's/$/\"/g')
DATETIME: $(date +"%d-%b-%Y %H:%M %Z" | sed -e 's/^/\"/g' -e 's/$/\"/g')
EOF
}

cortx_info() {
FILE_NAME=$1
cat <<EOF >> "$FILE_NAME"
KERNEL: $(ls cortx-motr-[0-9]*.rpm | sed -e  's/.*3/3/g' -e 's/.x86_64.rpm//g' -e 's/^/\"/g' -e 's/$/\"/g')
REQUIRES:
$(sed 's/^/    /g' "$SCRIPT_DIR"/../versioning/compatibility.info)
COMPONENTS:
$(ls -1 ./*.rpm | sed 's/\.\///g' | grep -v "$EXCLUDE_PACKAGES" |  awk '{ print "    - \""$1"\""}')
EOF
}

echo -e "Generating THIRD_PARTY_RELEASE.INFO file"

external_info() {
FILE_NAME=$1
cat <<EOF >> "$FILE_NAME"
THIRD_PARTY_COMPONENTS:
EOF
for dir in $(ls -1 | grep -E -v "repodata|*.INFO")
do
echo "Adding rpms from $dir"
cat <<EOF >> $FILE_NAME
    "$dir":
$(find -L ./"$dir" -type f -not -path "./lustre/tcp/*" -name '*.rpm' -or -name '*.tar.xz' -or -name '*.tgz' | awk -F '/' '{print $NF}' | sort | awk '{ print "       - \""$1"\""}')
EOF
done
}

pushd "$BUILD_LOCATION" || exit
create_metadata CORTX_RELEASE.INFO
cortx_info CORTX_RELEASE.INFO
cp CORTX_RELEASE.INFO ../external/rpm/RELEASE.INFO
popd || exit

pushd "$THIRD_PARTY_LOCATION" || exit 
create_metadata THIRD_PARTY_RELEASE.INFO
external_info THIRD_PARTY_RELEASE.INFO
external_info RELEASE.INFO
popd || exit

