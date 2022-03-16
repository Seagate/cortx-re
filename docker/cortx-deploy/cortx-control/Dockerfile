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
ARG ENVIRONMENT
ARG GIT_HASH
ARG BUILD_URL
ARG CREATED_DATE
ARG VERSION

LABEL maintainer="cortx.opensource@seagate.com" \
      org.opencontainers.image.title="CORTX CONTROL" \
      org.opencontainers.image.description="CORTX is 100% Open Source, Distributed object storage system designed for great efficiency, Massive capacity, and High HDD-utilization" \
      org.opencontainers.image.revision=$GIT_HASH \
      org.opencontainers.image.created=$CREATED_DATE

ENV GIT_HASH $GIT_HASH

#Disable default repositories. 
RUN yum install yum-utils -y \
    && if [[ $OS_TYPE =~ "centos" ]]; then yum-config-manager --disable base,extras,updates; fi

#Add Internal and CORTX uploads repository
COPY ./${ENVIRONMENT}/${OS}.repo /etc/yum.repos.d/
#RUN if [[ $OS_TYPE =~ "rocky" ]]; then cp /var/tmp/${ENVIRONMENT}/$(echo $OS_TYPE | cut -d'/' -f1)-${OS_RELEASE}.repo /etc/yum.repos.d/;else cp /var/tmp/${ENVIRONMENT}/${OS_TYPE}-${OS_RELEASE}.repo /etc/yum.repos.d/; fi

# Install rockylinux opensource-ci dependency
RUN if [[ "$ENVIRONMENT" = "opensource-ci" ]] && [[ $OS_TYPE == "rockylinux/rockylinux" ]]; then dnf install dnf-plugin-config-manager -y ;dnf config-manager --set-enabled powertools ; rm -rf /etc/yum.repos.d/$OS.repo ;fi

# Install EPEL repo for opensource-ci
RUN if [ "$ENVIRONMENT" = "opensource-ci" ] ; then yum install epel-release -y ; fi && yum clean all \
&& rm -rf /var/cache/yum

RUN yum install yum-utils -y \
    && yum-config-manager --add-repo ${BUILD_URL}/cortx_iso/ \
    && yum-config-manager --add-repo ${BUILD_URL}/3rd_party \
    && curl ${BUILD_URL}/RELEASE.INFO -o RELEASE.INFO 

COPY ./cortx-control/*.txt ./generate-release-info.sh /opt/seagate/cortx/

RUN yum install --nogpgcheck -y python3-pip \
    && pip3 install yq \
    && pip3 install  --no-cache-dir --trusted-host $(echo $BUILD_URL | awk -F '/' '{print $3}') -i $BUILD_URL/python_deps -r /opt/seagate/cortx/python_requirements.txt -r /opt/seagate/cortx/python_requirements.ext.txt \
   && yum clean all && rm -rf /var/cache/yum

RUN yum install --nogpgcheck -y  $(sed 's/#.*//g' /opt/seagate/cortx/third-party-rpms.txt) \
    && yum clean all && rm -rf /var/cache/yum
    
RUN yum install --nogpgcheck -y  $(sed 's/#.*//g' /opt/seagate/cortx/cortx-componenet-rpms.txt) \
    && yum clean all && rm -rf /var/cache/yum
    
RUN rm -f /etc/machine-id && ln -s /etc/cortx/solution/node/id /etc/machine-id \
    && bash /opt/seagate/cortx/generate-release-info.sh

RUN rm -rf /etc/yum.repos.d/$(echo $BUILD_URL | awk -F '/' '{print $3}')*.repo /etc/yum.repos.d/${OS}.repo \
    && yum clean all && rm -rf /var/cache/yum
