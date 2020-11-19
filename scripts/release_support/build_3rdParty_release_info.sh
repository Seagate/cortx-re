#!/bin/bash
BUILD_LOCATION=$1
echo -e "Generating RELEASE.INFO file"
SCRIPTPATH="$( cd "$(dirname "$0")" >/dev/null 2>&1 ; pwd -P )"
pushd $BUILD_LOCATION
#find . -type f -name *.rpm | awk -F '/' '{print $NF}' | awk '{ print "    - \""$1"\""}'

cat <<EOF > THIRD_PARTY_RELEASE.INFO
---
NAME: "CORTX"
VERSION: "1.0.0"
OS: $(cat /etc/redhat-release | sed -e 's/ $//g' -e 's/^/\"/g' -e 's/$/\"/g')
DATETIME: $(date +"%d-%b-%Y %H:%M %Z" | sed -e 's/^/\"/g' -e 's/$/\"/g')
THIRD_PARTY_COMPONENTS:
$(find . -type f -name "*.rpm" ! -path "./lustre/custom/tcp/*" | awk -F '/' '{print $NF}' | awk '{ print "    - \""$1"\""}')
EOF
mv THIRD_PARTY_RELEASE.INFO $SCRIPTPATH
popd
