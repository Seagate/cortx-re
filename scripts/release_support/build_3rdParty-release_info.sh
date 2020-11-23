#!/bin/bash
BUILD_LOCATION=$1

if [ -z $BUILD_LOCATION ]; then
echo "Build location is empty. exiting.."
exit 1
fi

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
EOF
popd


pushd $BUILD_LOCATION
for i in $(ls -1 | grep -E -v "repodata|THIRD_PARTY_RELEASE.INFO")
do
echo "Adding rpms from $i"
cat <<EOF >> THIRD_PARTY_RELEASE.INFO
    $i
$(find ./$i -type f -name "*.rpm" -or -name "*.tar.xz" ! -path "./lustre/custom/tcp/*" | awk -F '/' '{print $NF}' | awk '{ print "       - \""$1"\""}')
EOF
done
popd

pushd $BUILD_LOCATION
mv THIRD_PARTY_RELEASE.INFO $SCRIPTPATH
popd
