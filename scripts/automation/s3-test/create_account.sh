#!/bin/bash

S3_USER=$1
BUILD_NUMBER=$2
CORTX_USER_NAME=$3
CORTX_PASSWORD=$4

add_common_separator() {
    echo -e '\n--------------- '"$*"' ---------------\n'
}

check_status() {
    return_code=$?
    error_message=$1
    if [ $return_code -ne 0 ]; then
        add_common_separator ERROR: $error_message
    exit 1
    fi
    add_common_separator SUCCESS
}

if [ "$2x" == "x" ]; then
    echo "Please pass proper input"
    exit 1
fi

if [ -f bearertoken.log ]; then
    rm -rf bearertoken.log
fi

TOKEN=$(curl -s -i -k -d "{\"username\": \"${CORTX_USER_NAME}\", \"password\": \"${CORTX_PASSWORD}\"}" https://s3.seagate.com:31169/api/v2/login|awk '/Bearer/{print $(NF)}'|sed -e "s/\r//g")
if [ "x$TOKEN" == "x" ]; then
    echo "token not found in log file please check"
    exit 1
fi

curl -k -v -d "{\"uid\":\"${S3_USER}\", \"display_name\":\"${S3_USER}\", \"email\":\"${S3_USER}@seagate.com\"}" https://s3.seagate.com:31169/api/v2/s3/iam/users --header "Authorization: Bearer $TOKEN" > ${S3_USER}_${BUILD_NUMBER}.log
check_status "Access key & Secret key not created properly please look into that."
cat ${S3_USER}_${BUILD_NUMBER}.log