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
function login-pod {
        kubectl exec -it $1 ${2:-bash}
}

function stop-pod {
        kubectl delete pod $1
}

function delete-deply {
        kubectl delete deployment $1
}

function describe-pod {
        kubectl describe pod $1
}

function describe-all-pod {
        kubectl describe pods
}

function hctl {
        kubectl exec -it $(kubectl get pods | awk '/server-cortx/{print $1; exit}') -c cortx-hax -- hctl $1
}


function list-pods {
        kubectl get pods
}

function logs {
        kubectl logs $1
}

function pods {
        kubectl get pods
}

alias login-pod=login-pod

alias stop-pod=stop-pod

alias delete-deply=delete-deply

alias describe-pod=describe-pod

alias describe-all-pod=describe-all-pod

alias hctl=hctl $1

alias list-pods=list-pods

alias logs=logs

alias pods=pods
