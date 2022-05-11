#!/bin/bash
#
# Copyright (c) 2022 Seagate Technology LLC and/or its Affiliates
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

#Install reuired packages
curl -s https://packagecloud.io/install/repositories/github/git-lfs/script.rpm.sh | sudo bash
yum install gcc python3-devel git-lfs -y
pip3 install seaborn matplotlib pandas xlrd requests python-dateutil pyGithub jupyterlab
git lfs install && git lfs pull

#Clone Repo
git clone https://github.com/Seagate/cortx

#Execute notebook
pushd cortx/metrics
    mkdir report cache
    jupyter nbconvert --execute --to html --ExecutePreprocessor.timeout=18000 --output-dir=/tmp --no-input --output-dir=report --output Repo_Health Repo_Health.ipynb
popd
