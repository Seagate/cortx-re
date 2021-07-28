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

BUILD_LOCATION=$1
echo -e "Generating README.txt file"
pushd $BUILD_LOCATION
cat <<EOF > README.txt
CONTENTS OF THIS FILE
---------------------

 * Introduction
 * Artifacts
 * Provisioner Guide


Introduction
------------

Cortx Details to be added here.


Artifacts
------------
Release Build location:

http://cortx-storage.colo.seagate.com/releases/cortx/github/main/centos-7.8.2003/$BUILD_NUMBER/prod

Release Info file location:

http://cortx-storage.colo.seagate.com/releases/cortx/github/main/centos-7.8.2003/$BUILD_NUMBER/prod/RELEASE.INFO

Installation
-----------------

Provisioner Guide

https://seagate-systems.atlassian.net/wiki/spaces/PUB/pages/213385475/CORTX+Deployment

EOF