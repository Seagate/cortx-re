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

LABEL maintainer="cortx.opensource@seagate.com" \
      org.opencontainers.image.title="CORTX Image" \
      org.opencontainers.image.revision=$GIT_HASH

ENV GIT_HASH $GIT_HASH

#Disable default repositories. 
RUN yum-config-manager --disable base,extras,updates

#Add Internal and CORTX uploads repository
COPY ./docker/cortx-build/${ENVIRONMENT}/C${CENTOS_RELEASE}.repo /etc/yum.repos.d/

RUN yum-config-manager --add-repo ${BUILD_URL}/cortx_iso/ \
    && yum-config-manager --add-repo ${BUILD_URL}/3rd_party \
    && curl ${BUILD_URL}/RELEASE.INFO -o RELEASE.INFO 

COPY ./scripts/third-party-rpm/python_requirements*.txt /opt/

RUN yum install --nogpgcheck -y sudo \
    which \
    python3-pip \
    haproxy \ 
    && pip3 install  --no-cache-dir --trusted-host $(echo $BUILD_URL | awk -F '/' '{print $3}')  -i $BUILD_URL/python_deps -r /opt/python_requirements.txt -r /opt/python_requirements.ext.txt

RUN yum install --nogpgcheck -y http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/custom-deps/motr/libfabric/libfabric-1.11.2-1.el7.x86_64.rpm \
    && rm -rf /var/cache/yum

RUN yum install --nogpgcheck -y cortx-py-utils \
    cortx-hare \
    cortx-motr \ 
    cortx-s3server \
    cortx-csm_agent \
    java-1.8.0-openjdk-headless  \
    ipython3 \ 
    sshpass \ 
    python36-m2crypto \
    salt \ 
    salt-master \ 
    salt-minion \
    glusterfs-server \
    glusterfs-client \
    python36-cortx-prvsnr \
    cortx-prvsnr \
    cortx-prvsnr-cli \
    python36-cortx-setup \
    cortx-node-cli \
    cortx-cli \                 
    && rm -rf /var/cache/yum

RUN yum-config-manager --enable base,extras,updates && rm -rf /etc/yum.repos.d/cortx-storage.colo.seagate.com_releases_cortx_* \
    && rm -rf /var/cache/yum

