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

check_pcs_status(){
pcsStatusFull=$(pcs status --full)
service_filter=(
    "systemd:hare-hax"
    "systemd:s3authserver"
    "systemd:haproxy"
    "systemd:s3backgroundconsumer"
    "systemd:motr-free-space-monitor"
    "systemd:s3backgroundproducer"
    "systemd:sspl-ll"
    "systemd:kibana"
    "systemd:csm_agent"
    "systemd:csm_web"
    "systemd:event_analyzer"
    "service_instances_counter"
    "systemd:cortx_message_bus"
)

count_service_filter=(
    "motr-confd-count"
    "motr-ios-count"
    "s3server-count"
)

for service in ${service_filter[@]}; do
    service_state=$(echo "$pcsStatusFull"|grep "${service}"| awk '{print $3}')
    for state in ${service_state}; do
      if [ "${state}" != "Started" ]; then
          echo "Failed Service : ${service}"
          retry_status="yes"
      else
          echo "Working fine for ${service}"
      fi
    done
done

for service in ${count_service_filter[@]}; do
    service_count=$(echo "$pcsStatusFull"|grep "${service}"| awk '{print $4}')
    for servicecount in ${service_count}; do
      if [ "${servicecount}" -eq 0 ]; then
          echo "Failed Service : ${service}"
          retry_status="yes"
      else
          echo "Working fine for ${service}"
      fi
    done
done
}

first_try_status="yes"
count_retry=$1
count="0"
delay_time_sec=$2
retry_status="no"

while [ $count -lt $count_retry ] 
do
    if [[ $retry_status == "yes" ]] ; then
         sleep "$delay_time_sec"
         check_pcs_status
    elif [[ $first_try_status == "yes" ]] ; then
         sleep "$delay_time_sec"
         check_pcs_status
    else
         echo "Done working fine"
         break
    fi
    first_try_status="no"
    count=$((count+1))
    echo "retries $count"
    if [[ $count == $count_retry ]]; then
         echo "Retries timing is over"
    fi
done
