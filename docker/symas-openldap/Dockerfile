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
ARG CREATED_DATE
ARG SYMAS_VERSION

LABEL maintainer="cortx.opensource@seagate.com" \
      org.opencontainers.image.title="Symas Openldap Image" \
      org.opencontainers.image.revision=$GIT_HASH \
      org.opencontainers.image.created=$CREATED_DATE

ENV GIT_HASH $GIT_HASH

RUN yum-config-manager --add-repo https://repo.symas.com/configs/SOFL/rhel7/sofl.repo

COPY ./docker/symas-openldap/entrypoint.sh /opt/

COPY ./docker/symas-openldap/openldap-config /opt/openldap-config

RUN yum install --nogpgcheck -y  symas-openldap-clients-$SYMAS_VERSION \
    symas-openldap-servers-$SYMAS_VERSION \
    sysvinit-tools \
    && yum clean all \
    && rm -rf /var/cache/yum

RUN yum clean all \
    && rm -rf /var/cache/yum
