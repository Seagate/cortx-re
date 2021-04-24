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

SETUP_LOG_FILE="$1"
DEPLOYMENT_STATUS_FILE="/root/cortx_deployment/log/deployment_status.log"
FAILED_COMPONENT_FILE="/root/cortx_deployment/log/failed_component.log"
IS_FAILED=$(grep -i 'Salt client command failed' ${SETUP_LOG_FILE})
IS_SUCCESS=$(grep -i 'Deploy VM - Done' ${SETUP_LOG_FILE})
IS_PROVISIONER_ERROR=$(grep -i 'PROVISIONER FAILED' ${SETUP_LOG_FILE})


if [[ ${IS_SUCCESS} ]]; then

    DEPLOYMENT_STATUS="Cortx Stack VM Deployment 'Success'."

elif [[ ${IS_FAILED} ]]; then

    LAST_APPLIED_COMPONENT=$(cat ${SETUP_LOG_FILE} | grep 'Applying' | tail -1 | cut -d"'" -f2)
    LOG_START_LINE=$(grep -n -E "provisioner.errors.SaltCmdResultError: salt command failed, reason|provisioner.errors.SaltCmdRunError: salt command failed, reason" "${SETUP_LOG_FILE}" | tail -1 | cut -f1 -d:)
    LOG_DATA=$(tail -n "+${LOG_START_LINE}" ${SETUP_LOG_FILE})
    DEPLOYMENT_STATUS="Cortx Stack VM Deployment 'Failed' in '${LAST_APPLIED_COMPONENT}'. Please check setup log for more info. \n Log :\n -----\n\n ${LOG_DATA}\n -----\n"
    echo ${LAST_APPLIED_COMPONENT} >> "${FAILED_COMPONENT_FILE}"

elif [[ ${IS_PROVISIONER_ERROR} ]]; then

    LAST_APPLIED_COMPONENT="bootstrap"
    LOG_START_LINE=$(grep -n "Reason: salt command failed, reason" "${SETUP_LOG_FILE}" | tail -1 | cut -f1 -d:)
    LOG_DATA=$(tail -n "+${LOG_START_LINE}" ${SETUP_LOG_FILE})
    DEPLOYMENT_STATUS="Cortx Stack VM Deployment 'Failed' in '${LAST_APPLIED_COMPONENT}'. Please check setup log for more info. \n Log :\n -----\n\n ${LOG_DATA}\n -----\n"
    echo ${LAST_APPLIED_COMPONENT} >> "${FAILED_COMPONENT_FILE}"

else

    DEPLOYMENT_STATUS="Cortx Stack VM Deployment 'Failed'. Please check setup log for more info."
    echo 'bootstrap' >> "${FAILED_COMPONENT_FILE}"
fi

echo -en "Deployment Status :\n \t ${DEPLOYMENT_STATUS} \n" 2>&1 | tee -a "${DEPLOYMENT_STATUS_FILE}"

