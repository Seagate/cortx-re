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
ARG CORTX_ARTIFACT_REPO
ARG EXTERNAL_ARTIFACT_REPO

LABEL maintainer="cortx.opensource@seagate.com" \
      org.opencontainers.image.title="CORTX Image" \
      org.opencontainers.image.revision=$GIT_HASH

ENV GIT_HASH $GIT_HASH

COPY ./docker/cortx-deploy/pip.conf /etc/pip.conf

RUN yum-config-manager --add-repo ${CORTX_ARTIFACT_REPO}/cortx_iso/ \
    && yum-config-manager --add-repo ${EXTERNAL_ARTIFACT_REPO}

RUN yum install python3-pip --nogpgcheck -y \
    && pip3 install -r https://raw.githubusercontent.com/Seagate/cortx-utils/main/py-utils/python_requirements.txt \
    && pip3 install -r https://raw.githubusercontent.com/Seagate/cortx-utils/main/py-utils/python_requirements.ext.txt

RUN yum install --nogpgcheck -y http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/custom-deps/motr/libfabric/libfabric-1.11.2-1.el7.x86_64.rpm \
    && rm -rf /var/cache/yum

RUN yum install --nogpgcheck -y cortx-py-utils \
    cortx-hare \
    cortx-motr \ 
    cortx-s3server \
    && rm -rf /var/cache/yum

RUN rm -rf /etc/yum.repos.d/cortx-storage.colo.seagate.com_releases_cortx_* \
    && rm -rf /var/cache/yum

