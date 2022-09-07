#!/bin/bash
#
# Copyright (c) 2021 Seagate Technology LLC and/or its Affiliates
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

source /var/tmp/functions.sh

CORTX_MANAGER_IP=$(kubectl get svc | grep cortx-control-loadbal-svc | awk '{ print $3}')
CORTX_MANAGER_PORT="8081"
CORTX_MANAGER_ENDPOINT="https://$CORTX_MANAGER_IP:$CORTX_MANAGER_PORT"
MANAGEMENT_CHECK_STATUS="management-path-status.txt"

rm -f $MANAGEMENT_CHECK_STATUS

function run_curl_query() {
add_secondary_separator $1
curl -k -s $CORTX_MANAGER_ENDPOINT/$2 | jq | tee -a $MANAGEMENT_CHECK_STATUS
check_status "$1 execution failed. Exiting"
} 

#add_secondary_separator "Deployment Query"
#time curl -k -s $CORTX_MANAGER_ENDPOINT/api/v2/system/topology/nodes | jq 

#add_secondary_separator "Storage Set Query"
#time curl -k -s $CORTX_MANAGER_ENDPOINT/api/v2/system/topology/storage_sets | jq

run_curl_query "Deployment Query" "api/v2/system/topology/nodes"
run_curl_query "Storage Set Query" "api/v2/system/topology/storage_sets"


