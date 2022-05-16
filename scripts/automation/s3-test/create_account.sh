#!/bin/bash
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
# ./create_s3_account.sh username email ldap_user ldap_pwd

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

if [ "$4x" == "x" ]; then
    echo "ERROR: Required inputs not provided properly. Please provide inputs like below."
    echo "./create_account.sh S3_USER 0 CORTX_USER_NAME CORTX_PASSWORD"
    exit 1
fi

TOKEN=$(curl -s -i -k -d "{\"username\": \"${CORTX_USER_NAME}\", \"password\": \"${CORTX_PASSWORD}\"}" https://s3test.seagate.com:31169/api/v2/login|awk '/Bearer/{print $(NF)}'|sed -e "s/\r//g")
if [ "x$TOKEN" == "x" ]; then
    echo "token not getting proper, Need to check application logs"
    exit 1
fi

curl -k -v -d "{\"uid\":\"${S3_USER}\", \"display_name\":\"${S3_USER}\", \"email\":\"${S3_USER}@seagate.com\"}" https://s3test.seagate.com:31169/api/v2/s3/iam/users --header "Authorization: Bearer $TOKEN" > ${S3_USER}_${BUILD_NUMBER}.log
errorvar=$(cat ${S3_USER}_${BUILD_NUMBER}.log)
check_status "Access key & Secret key not created properly, please below logs \n $errorvar"