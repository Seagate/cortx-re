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

ARG OS_TYPE
ARG OS_RELEASE

FROM ${OS_TYPE}:${OS_RELEASE}

ARG OS
ARG OS_TYPE
ARG OS_RELEASE
ARG CORTX_IMAGE
ARG ENVIRONMENT
ARG GIT_HASH
ARG CORTX_VERSION
ARG BUILD_URL
ARG CREATED_DATE
ARG VERSION

LABEL maintainer="cortx.opensource@seagate.com" \
      org.opencontainers.image.title="$CORTX_IMAGE" \
      org.opencontainers.image.description="CORTX is 100% Open Source, Distributed object storage system designed for great efficiency, Massive capacity, and High HDD-utilization" \
      org.opencontainers.image.revision=$GIT_HASH \
      org.opencontainers.image.version=$CORTX_VERSION \ 
      org.opencontainers.image.created=$CREATED_DATE

ENV GIT_HASH $GIT_HASH

#Add Internal and CORTX uploads repository
COPY ./${ENVIRONMENT}/$OS.repo /etc/yum.repos.d/

# Install rockylinux opensource-ci dependency
RUN if [[ "$ENVIRONMENT" = "opensource-ci" ]] && [[ $OS_TYPE == "rockylinux/rockylinux" ]]; then dnf install dnf-plugin-config-manager -y ;dnf config-manager --set-enabled powertools ; rm -rf /etc/yum.repos.d/$OS.repo ;fi

# Install EPEL repo for opensource-ci
RUN if [ "$ENVIRONMENT" = "opensource-ci" ] ; then yum install epel-release -y ; fi && yum clean all \
&& rm -rf /var/cache/yum

RUN yum install yum-utils -y \
    && yum-config-manager --add-repo ${BUILD_URL}/cortx_iso/ \
    && yum-config-manager --add-repo ${BUILD_URL}/3rd_party \
    && curl ${BUILD_URL}/RELEASE.INFO -o RELEASE.INFO 

COPY ./*.txt ./${CORTX_IMAGE}/*.txt ./compatibility.info ./generate-release-info.sh /opt/seagate/cortx/

RUN yum install --nogpgcheck -y python3-pip \
    && pip3 install yq==2.14.0 \
    && while read component; do grep "$component" < /opt/seagate/cortx/python_requirements.txt; done </opt/seagate/cortx/cortx-component-rpms.txt | sed 's/#.*//g' > /opt/seagate/cortx/requirements.txt \
    && while read component; do grep "$component" < /opt/seagate/cortx/python_requirements.ext.txt; done </opt/seagate/cortx/cortx-component-rpms.txt | sed 's/#.*//g' > /opt/seagate/cortx/requirements.ext.txt \
    && pip3 install  --no-cache-dir --trusted-host $(echo $BUILD_URL | awk -F '/' '{print $3}') -i $BUILD_URL/python_deps -r /opt/seagate/cortx/requirements.txt -r /opt/seagate/cortx/requirements.ext.txt \
    && yum clean all && rm -rf /var/cache/yum

RUN if [[ "${CORTX_IMAGE}" = "cortx-rgw" ]] || [[ "${CORTX_IMAGE}" = "cortx-all" ]] || [[ "${CORTX_IMAGE}" = "cortx-data" ]]; then yum --nogpgcheck -y --disablerepo="EOS_Rocky_8_OS_x86_64_Rocky_8" install libfabric-1.11.2; fi \
    && yum install --nogpgcheck -y  $(while read -r component; do grep "$component" < /opt/seagate/cortx/third-party-rpms.txt; done </opt/seagate/cortx/cortx-component-rpms.txt | sed 's/#.*//g') \
    && yum clean all && rm -rf /var/cache/yum
    
RUN yum install --nogpgcheck -y  $(sed 's/#.*//g' /opt/seagate/cortx/cortx-component-rpms.txt) \
    && if [[ "${CORTX_IMAGE}" = "cortx-rgw" ]]; then yum install --nogpgcheck -y  $(sed 's/#.*//g' /opt/seagate/cortx/ceph-component-rpms.txt); fi \
    && yum clean all && rm -rf /var/cache/yum
    
RUN rm -f /etc/machine-id && ln -s /etc/cortx/solution/node/id /etc/machine-id \
    && bash /opt/seagate/cortx/generate-release-info.sh

RUN rm -rf /etc/yum.repos.d/$(echo $BUILD_URL | awk -F '/' '{print $3}')*.repo /etc/yum.repos.d/$OS.repo \
    && yum clean all && rm -rf /var/cache/yum
