#!/bin/bash
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

source /var/tmp/functions.sh

function install_metricsserver() {
    add_primary_separator "Installing Metrics Server"
    echo $RAW_CORTX_RE_REPO/$CORTX_RE_BRANCH
    kubectl apply -f "$RAW_CORTX_RE_REPO/$CORTX_RE_BRANCH/solutions/kubernetes/metrics-server/metric-server-components.yaml"
    POD_NAME=$(kubectl get pods --namespace=kube-system | grep metrics-server | cut -d " " -f 1)
    kubectl wait --for=condition=Ready pod/"$POD_NAME" --namespace=kube-system --timeout=60s
    COUNT=0
    until kubectl top node 2>/dev/null || ((COUNT++ >= 5)); do
        sleep 5
    done
    kubectl -n kubernetes-dashboard get pods &>/dev/null
    check_status "Getting error in Metrics Server, Please check!"
    add_secondary_separator "Deployed Metrics Server successfully"
}
function install_k8dashboard() {
    add_primary_separator "Installing K8s Dashboard"
    kubectl apply -f https://raw.githubusercontent.com/kubernetes/dashboard/v2.5.0/aio/deploy/recommended.yaml
    kubectl -n kubernetes-dashboard get pods
    check_status "Getting error in K8s dashboard, Please check!"

    DOMAIN=$(kubectl -n kubernetes-dashboard get pods -o wide | awk '$1 ~/kubernetes-dashboard/ {print $7}')
    kubectl -n kubernetes-dashboard patch svc kubernetes-dashboard -p '{"spec": {"ports": [{"port": 443,"targetPort": 8443,"nodePort": 32323}],"type": "NodePort"}}'
    kubectl apply -f "$RAW_CORTX_RE_REPO/$CORTX_RE_BRANCH/solutions/kubernetes/metrics-server/cluster_admin_svc_acnt.yaml"
    TOKEN=$(kubectl -n kube-system describe sa dashboard-admin | awk '/Tokens/ {print $2}')
    SECRET=$(kubectl -n kube-system describe secret "$TOKEN" | grep token | tail -n +3 | awk '{print $2}')
    add_primary_separator "Your k8s Dashboard is ready, Access it via https://$DOMAIN:32323"
    add_secondary_separator "Token is $SECRET"
}
install_metricsserver
install_k8dashboard
