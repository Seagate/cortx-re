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

ARG CENTOS_RELEASE

FROM centos:${CENTOS_RELEASE}

ARG CENTOS_RELEASE
ARG ENVIRONMENT
ARG GIT_HASH
ARG BUILD_URL
ARG CREATED_DATE

LABEL maintainer="cortx.opensource@seagate.com" \
      org.opencontainers.image.title="CORTX ALL" \
      org.opencontainers.image.description="CORTX is 100% Open Source, Distributed object storage system designed for great efficiency, Massive capacity, and High HDD-utilization" \
      org.opencontainers.image.revision=$GIT_HASH \
      org.opencontainers.image.created=$CREATED_DATE

ENV GIT_HASH $GIT_HASH

#Disable default repositories. 
RUN yum-config-manager --disable base,extras,updates

#Add Internal and CORTX uploads repository
COPY ./docker/cortx-deploy/${ENVIRONMENT}/C${CENTOS_RELEASE}.repo /etc/yum.repos.d/

RUN yum-config-manager --add-repo ${BUILD_URL}/cortx_iso/ \
    && yum-config-manager --add-repo ${BUILD_URL}/3rd_party \
    && curl ${BUILD_URL}/RELEASE.INFO -o RELEASE.INFO 

COPY ./scripts/third-party-rpm/python_requirements*.txt /opt/

RUN yum install --nogpgcheck -y python3-pip \
    && pip3 install yq \
    && pip3 install  --no-cache-dir --trusted-host $(echo $BUILD_URL | awk -F '/' '{print $3}') -i $BUILD_URL/python_deps -r /opt/python_requirements.txt -r /opt/python_requirements.ext.txt \
   && yum clean all && rm -rf /var/cache/yum

RUN yum install --nogpgcheck -y java-1.8.0-openjdk-headless \
    sudo \
    which \
    haproxy22 \
    python3 \
    sshpass \
    python36-m2crypto \
    jq \
    python36-dbus \
    cortx-provisioner \
    cortx-py-utils \
    cortx-hare \
    cortx-motr \
    cortx-s3server \
    cortx-csm_agent \
    python36-cortx-prvsnr \
    && yum clean all && rm -rf /var/cache/yum

RUN yum-config-manager --enable base,extras,updates && rm -rf /etc/yum.repos.d/cortx-storage.colo.seagate.com_releases_cortx_* /etc/yum.repos.d/C${CENTOS_RELEASE}.repo \
    && yum clean all && rm -rf /var/cache/yum
