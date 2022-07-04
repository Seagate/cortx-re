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

set -eo pipefail

if [ -z "$GH_OATH" ]; then echo "GH_OATH is not set. Existing..."; exit 1; fi
if [ -z "$CODACY_OATH" ]; then echo "CODACY_OATH is not set. Existing..."; exit 1; fi
if [ -z "$CORTX_REPO" ]; then echo "CORTX_REPO is not set. Existing..."; exit 1; fi
if [ -z "$CORTX_BRANCH" ]; then echo "CORTX_BRANCH is not set. Existing..."; exit 1; fi

#Install reuired packages
curl -s https://packagecloud.io/install/repositories/github/git-lfs/script.rpm.sh | sudo bash
yum install gcc python3-devel git-lfs pango-devel -y
pip3 install seaborn matplotlib pandas xlrd requests python-dateutil pyGithub jupyterlab django-weasyprint weasyprint==52.5
git lfs install

#Clone Repo
git clone $CORTX_REPO -b $CORTX_BRANCH --depth=1
pushd cortx && git lfs pull && popd

#Execute notebook
pushd cortx/metrics
    mkdir report cache
    jupyter nbconvert --debug --log-level 10 --execute --to html --ExecutePreprocessor.timeout=18000 --output-dir=/tmp --no-input --output-dir=report --output Repo_Health Repo_Health.ipynb
#Generate PDF
    cp report/Repo_Health.html /tmp/ && python3 ./html_to_pdf.py
popd