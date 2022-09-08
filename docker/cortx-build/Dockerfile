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

ARG OS_RELEASE
ARG OS_IMAGE

FROM ${OS_IMAGE}:${OS_RELEASE} AS base

ARG OS_TYPE
ARG OS_RELEASE
ARG GPG_PASSPHRASE

ARG ENVIRONMENT
ARG GIT_HASH

LABEL maintainer="cortx.opensource@seagate.com" \
      org.opencontainers.image.title="CORTX Build Image" \
      org.opencontainers.image.revision=$GIT_HASH \
      org.opencontainers.image.vendor="Seagate" \
      org.opencontainers.image.description="CORTX Build image is used to compile rpm packages from source code for CORTX stack components" \
      org.opencontainers.image.source="https://github.com/Seagate/cortx/"
      

ENV BASH_ENV "/etc/profile"
ENV GIT_HASH $GIT_HASH
ENV PATH="/root/.nvm/versions/node/v12.13.0/bin:${PATH}"
RUN if [[ $OS_TYPE == "centos" ]]; then export LANG=en_US.UTF-8 ; export LC_ALL=en_US.UTF-8 ; fi

#Disable default repositories. 
RUN if [[ $OS_TYPE == "centos" ]]; then rm -rf /etc/yum.repos.d/CentOS-* ;elif [[ $OS_TYPE == "rockylinux" ]]; then rm -rf /etc/yum.repos.d/${OS_TYPE}-* ; fi

#Add Internal and CORTX uploads repository
COPY ./docker/cortx-build/${ENVIRONMENT}/$OS_TYPE-$OS_RELEASE.repo /etc/yum.repos.d/

#Rocky Linux opensource dependecy added
RUN if [[ "$ENVIRONMENT" = "opensource-ci" ]] && [[ $OS_TYPE == "rockylinux" ]]; then dnf install dnf-plugin-config-manager -y ;dnf config-manager --set-enabled powertools ; rm -rf /etc/yum.repos.d/$OS_TYPE-$OS_RELEASE.repo ;fi

# Install EPEL repo for opensource-ci
RUN if [ "$ENVIRONMENT" = "opensource-ci" ] ; then yum install epel-release -y ; fi && yum clean all \
&& rm -rf /var/cache/yum

# Enable SCL repos
RUN if [[ $OS_TYPE == "centos" ]]; then yum install centos-release-scl -y && yum clean all && rm -rf /var/cache/yum ; fi

# install common tools
RUN yum -y install createrepo \
file \
git \
jq \
wget \
which \
openssh-server \
java-1.8.0-openjdk \
make \
gcc \
cmake \
gcc-c++ \
sudo \
rpm-build \
rpmdevtools \
scl-utils \
libtool \
doxygen \
openssl \
openssl-devel \
graphviz \
check-devel \
userspace-rcu-devel \
libcmocka.x86_64 \
libcmocka-devel.x86_64 \
jemalloc \
json-c-devel \
json-c \
cmake3 \
jemalloc-devel \
libini_config-devel \
autoconf \
bison \
flex  \
dbus-devel \
expect \
rpm-sign \
genisoimage ; if [[ $OS_TYPE == "centos" ]]; then yum -y install python38{,-{devel,pip,setuptools}} \
sclo-git212 \
python3-Levenshtein \
python3-pep8 \
yum-priorities \
redhat-ls && yum clean all \
&& rm -rf /var/cache/yum ; elif [[ $OS_TYPE == "rockylinux" ]]; then dnf -y install python38{,-{devel,pip,setuptools}} \
yum-utils \
python3-Levenshtein \
python3-pycodestyle \
glibc-langpack-en \
glibc-locale-source \
pinentry \
kernel-abi-whitelists \
libmodulemd \
redhat-lsb && dnf clean all \
&& rm -rf /var/cache/yum ; fi \
&& python3 -m pip install -U pip setuptools

# enable Git from SLC repo (it's more up to date version)
RUN if [[ $OS_TYPE == "centos" ]]; then ln -nsf /opt/rh/sclo-git212/root/bin/* /usr/local/bin/ && \
    ln -nsf /opt/rh/sclo-git212/enable /etc/profile.d/sclo-git212.sh ; fi

#Remove sclo repositories
RUN if [[ $OS_TYPE == "centos" ]]; then yum-config-manager --disable centos-sclo* ; fi

# set locale
RUN localedef -i en_US -f UTF-8 en_US.UTF-8

# set up rpmbuild directory
RUN rpmdev-setuptree

# Copy scripts
COPY ./docker/cortx-build/*.sh /opt/

# install node
RUN bash /opt/install-node.sh v12.13.0 \
&& rm -rf /opt/install-node.sh \
&& rm -rf install.sh

# docker-systemctl-replacement
RUN sh /opt/systemctl-replacement.sh \
&& rm -f /opt/systemctl-replacement.sh \
&& rm -f systemctl.py

# yum repo from GitHub Release 
RUN bash /opt/set-githubrelease-repo.sh \
&& yum clean all \
&& rm -rf /var/cache/yum \
&& rm -f /opt/set-githubrelease-repo.sh

# rpm signing
COPY ./scripts/rpm-signing/* /opt/rpm-signing/

RUN if [[ $OS_TYPE == "centos" ]]; then cat /opt/rpm-signing/gpgoptions >>  ~/.rpmmacros \
&& sed -i "/passphrase/d" /opt/rpm-signing/genkey-batch \
&& sed -i -e 's/$argv 1/$argv 0/g' -e 's/${PASSPHRASE}/ /g' -e '/PASSPHRASE/d' /opt/rpm-signing/rpm-sign.sh ; elif [[ $OS_TYPE == "rockylinux" ]]; then sed 's/--passphrase-fd 3 //g' /opt/rpm-signing/gpgoptions >> ~/.rpmmacros \
&& sed -i -e 's/Passphrase:.*/Passphrase: ${GPG_PASSPHRASE}/g' /opt/rpm-signing/genkey-batch ; fi \
&& /usr/bin/gpg --batch --gen-key /opt/rpm-signing/genkey-batch \
&& /usr/bin/gpg --export -a 'Seagate'  > /opt/rpm-signing/RPM-GPG-KEY-Seagate \
&& rpm --import /opt/rpm-signing/RPM-GPG-KEY-Seagate

# release helper scripts
COPY ./scripts/release_support/build_release_info.sh /opt/release_support/

# cortx-prereq package

COPY ./scripts/third-party-rpm/ /opt/third-party-rpm

# Makefile for build
COPY ./docker/cortx-build/Makefile . 

FROM base as lustre-rpm-builder

ARG LUSTRE_VERSION 
ARG ENVIRONMENT

ADD https://downloads.whamcloud.com/public/lustre/lustre-${LUSTRE_VERSION}/el7/client/SRPMS/lustre-${LUSTRE_VERSION}-1.src.rpm . 
RUN rpm -i lustre-${LUSTRE_VERSION}-1.src.rpm 

COPY ./docker/cortx-build/lustre-2.9.0.spec.patch .

RUN patch -p1 -i lustre-2.9.0.spec.patch /root/rpmbuild/SPECS/lustre.spec

RUN yum -y install kernel{,-devel}  \
	&& yum-builddep -y /root/rpmbuild/SPECS/lustre.spec \
        && yum clean all \
        && rm -rf /var/cache/yum
	
RUN if [[ $OS_TYPE == "centos" ]]; then QA_RPATHS="255" rpmbuild -bb --without servers --without lustre-tests \
        --define 'configure_args --disable-gss-keyring' \
        --define "kver $(ls -1r /lib/modules | head -n1)" \
        /root//rpmbuild/SPECS/lustre.spec ; fi

FROM base as io-build 

ARG LUSTRE_VERSION

#COPY --from=lustre-rpm-builder /root/rpmbuild/RPMS/x86_64/lustre-client-devel-${LUSTRE_VERSION}*.rpm . 

#Install luster packages
RUN if [[ $OS_TYPE == "centos" ]]; then yum install -y https://downloads.whamcloud.com/public/lustre/lustre-${LUSTRE_VERSION}/el7/client/RPMS/x86_64/kmod-lustre-client-${LUSTRE_VERSION}-1.el7.x86_64.rpm \
         https://downloads.whamcloud.com/public/lustre/lustre-${LUSTRE_VERSION}/el7/client/RPMS/x86_64/lustre-client-${LUSTRE_VERSION}-1.el7.x86_64.rpm \
		 && yum install -y lustre-client-devel-${LUSTRE_VERSION}*.rpm \
		 && yum clean all \
		 && rm -rf /var/cache/yum ; fi
		 
RUN if [[ $OS_TYPE == "centos" ]]; then curl -k https://raw.githubusercontent.com/Seagate/cortx-motr/main/cortx-motr.spec.in -o cortx-motr.spec \
	&& sed -i 's/@.*@/111/g' cortx-motr.spec \
	&& kernel_src=$(ls -1rd /lib/modules/*/build | head -n1) \
        && yum-builddep -y cortx-motr.spec \
	&& yum clean all \
        && rm -rf /var/cache/yum ; fi
