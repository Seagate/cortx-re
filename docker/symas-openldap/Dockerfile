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
      org.opencontainers.image.title="Symas Openldap Image" \
      org.opencontainers.image.revision=$GIT_HASH \
      org.opencontainers.image.created=$CREATED_DATE

ENV GIT_HASH $GIT_HASH

RUN yum-config-manager --add-repo http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/centos/centos-7.9.2009-2.0.0-k8/

COPY ./docker/symas-openldap/entrypoint.sh /opt/

COPY ./docker/symas-openldap/openldap-config /opt/openldap-config

RUN yum install --nogpgcheck -y  symas-openldap-clients \
    symas-openldap-servers \
    sysvinit-tools \
    && yum clean all \
    && rm -rf /var/cache/yum

RUN rm -rf /etc/yum.repos.d/cortx-storage.colo.seagate.com_releases_cortx_* \
    && yum clean all \
    && rm -rf /var/cache/yum
