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
ARG USER_PASS

# disable all repos except for the specific release we're targeting
# RUN yum-config-manager --disable updates
#


# Remove all public repos.
RUN rm -rf /etc/yum.repos.d/*.repo
# COPY C${CENTOS_RELEASE}.repo eos_${CENTOS_RELEASE}.repo python3_uploads.repo eos_uploads.repo /etc/yum.repos.d/
COPY C${CENTOS_RELEASE}.repo /etc/yum.repos.d/
#

# upgrade all
RUN yum --assumeyes upgrade --exclude=centos-release && \
    yum clean all \
    && rm -rf /var/cache/yum

# enable EPEL and SCL repos
RUN yum --assumeyes install epel-release centos-release-scl && \
    yum clean all \
    && rm -rf /var/cache/yum

# workaround broken CentOS 7.6 compatibility witn EPEL
#RUN if (( $(grep -Po '(?<=release 7\.).' /etc/redhat-release) < 7 )) ; then \
#        yum install -y --disablerepo='*' --enablerepo=base --enablerepo=updates \
#            python3{,-{devel,pip,setuptools}} ; \
#    fi

# install common tools
RUN yum --assumeyes install python3{,-{devel,pip,setuptools}} \
createrepo \
file \
git \
jq \
rpm-build \
rpmdevtools \
scl-utils \
sclo-git212 \
wget \
which \
openssh-server \
java-1.8.0-openjdk \
make \
gcc \
cmake \
gcc-c++ && \
yum clean all \
&& rm -rf /var/cache/yum

# enable Git from SLC repo (it's more up to date version)
RUN ln -nsf /opt/rh/sclo-git212/root/bin/* /usr/local/bin/ \
    && ln -nsf /opt/rh/sclo-git212/enable /etc/profile.d/sclo-git212.sh

#Remove sclo repositories
RUN yum-config-manager --disable centos-sclo*

# set locale
RUN localedef -i en_US -f UTF-8 en_US.UTF-8
ENV LANG=en_US.UTF-8 \
    LC_ALL=en_US.UTF-8

# set up rpmbuild directory
RUN rpmdev-setuptree

CMD ["/usr/sbin/init"]
ENTRYPOINT ["/bin/bash", "-c"]
