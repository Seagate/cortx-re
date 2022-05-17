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
source /root/cortx-re/solutions/kubernetes/functions.sh

#Download WhiteSource Plugin 
wget http://cortx-storage.colo.seagate.com/releases/cortx/security/whitesource/ws-k8s-agent.tar && tar -xvf ws-k8s-agent.tar >/dev/null 2>&1

#Updating the configuration file
sed -Ei 's,(url: ).*,\1"'"$WHITESOURCE_SERVER_URL"'",g' /root/ws-k8s-agent/helm-chart/values.yaml; sed -Ei 's,(apiKey: ).*,\1"'"$API_KEY"'",g' /root/ws-k8s-agent/helm-chart/values.yaml
sed -Ei 's,(userKey: ).*,\1"'"$USER_KEY"'",g' /root/ws-k8s-agent/helm-chart/values.yaml; sed -Ei "s,(productName: ).*,\1$PRODUCT_NAME,g" /root/ws-k8s-agent/helm-chart/values.yaml
sed -Ei "s,(registry: ).*,\1$DOCKER_REGISTRY,g" /root/ws-k8s-agent/helm-chart/values.yaml; sed -Ei "s,(mainPod: ).*,\1$MAIN_POD,g" /root/ws-k8s-agent/helm-chart/values.yaml
sed -Ei "s,(workerPod: ).*,\1$WORKER_POD,g" /root/ws-k8s-agent/helm-chart/values.yaml; sed -Ei 's,(pullSecret: ).*,\1"'"$PULL_SECRET"'",g' /root/ws-k8s-agent/helm-chart/values.yaml

#print values.yaml
cat /root/ws-k8s-agent/helm-chart/values.yaml | egrep -iw "url: |apiKey: |userKey: |pullSecret: |productName:|registry:|mainPod:|workerPod:"| head -8

#Pulling whitesource images https://ghcr.io/v2/
docker pull ghcr.io/seagate/whitesource-pre-configure:20.11.1 && docker pull ghcr.io/seagate/whitesource-main:20.11.1 && docker pull ghcr.io/seagate/whitesource-worker:20.11.1

#Uninstall/stop existing scanner
#pushd root/ws-k8s-agent
#helm uninstall whitesource-k8s
add_common_separator "Run helm uninstall whitesource-k8s to stop existing scanner"

#Running the hem-chart to setup whiteSource Containers & trigger the Scan
pushd ws-k8s-agent
#helm install whitesource-k8s ./helm-chart –wait
#Kubectl get pods -n whitesource-namespace

add_common_separator "Run helm install whitesource-k8s ./helm-chart –wait to setup whiteSource Containers & trigger the Scan"
