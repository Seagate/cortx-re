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

set -eo pipefail

source /var/tmp/functions.sh

CORTX_MANAGER_IP=$(kubectl get svc cortx-control -o=jsonpath='{.spec.clusterIP}')
CORTX_MANAGER_PORT=$(kubectl get svc cortx-control -o=jsonpath='{.spec.ports[?(@.name=="control-https")].port}')
CORTX_MANAGER_ENDPOINT="https://$CORTX_MANAGER_IP:$CORTX_MANAGER_PORT"

function validate_packages() {
    PACKAGE_LIST=$*
    for package in $PACKAGE_LIST; do rpm -q $package || yum install $package -y || (add_common_separator "Failed to install $package. Exiting.."; exit 1); done
}


#Function to execute API queries using curl
function run_curl_query() {
    add_secondary_separator "Executing $1"
    HTTP_RESPONSE=$(curl -k -s -w "%{http_code}" $CORTX_MANAGER_ENDPOINT/$1 -o output.json)
    jq . output.json
    [ "$HTTP_RESPONSE" == "200" ] || (add_common_separator "ERROR: $1 execution failed. Exiting"; exit 1)
} 

#Validate and install required packages
validate_packages jq curl

#Execute API queries
run_curl_query "api/v2/system/topology"
