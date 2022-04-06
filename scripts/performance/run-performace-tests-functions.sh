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
source /var/tmp/functions.sh

function usage() {
    cat << HEREDOC
Usage : $0 [--setup-client, --fetch-setup-info]
where,
    --setup-client - Setup S3 Client.
    --fetch-setup-info - Fetch setup information for PerfPro.
HEREDOC
}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

function fetch_build_url() {
    RGW_IMAGE=$(kubectl get pod $(kubectl get pods | awk '/cortx-server/{print $1; exit}') -o jsonpath="{.spec.containers[*].image}" | tr ' ' '\n' | uniq)
    BUILD_ID=$(docker run --rm $RGW_IMAGE cat /RELEASE.INFO | grep BUILD | cut -d' ' -f2 | sed 's/"//g')
    BUILD_URL="http://cortx-storage.colo.seagate.com/releases/cortx/github/main/rockylinux-8.4/$BUILD_ID/prod/"
}

function create_endpoint_url() {
    echo SOLUTION_FILE=$SOLUTION_FILE
    ACCESS_KEY=$(yq e '.solution.common.s3.default_iam_users.auth_admin' $SOLUTION_FILE)
    SECRET_KEY=$(yq e '.solution.secrets.content.s3_auth_admin_secret' $SOLUTION_FILE)
    HTTP_PORT=$(kubectl get svc cortx-io-svc-0 -o=jsonpath='{.spec.ports[?(@.port==80)].nodePort}')
    IP_ADDRESS=$(ifconfig eth1 | grep inet -w | awk '{print $2}')
    ENDPOINT_URL="http://$IP_ADDRESS:$HTTP_PORT"

    echo ENDPOINT_URL=$ENDPOINT_URL
    echo ACCESS_KEY=$ACCESS_KEY
    echo SECRET_KEY=$SECRET_KEY
    echo BUILD_URL=$BUILD_URL
}

function clone_segate_tools_repo() {
    if [ -z "$SCRIPT_LOCATION" ]; then echo "SCRIPT_LOCATION not provided.Exiting..."; exit 1; fi
    if [ -z "$GITHUB_TOKEN" ]; then echo "GITHUB_TOKEN not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_TOOLS_REPO" ]; then echo "CORTX_TOOLS_REPO not provided.Exiting..."; exit 1; fi
    if [ -z "$CORTX_TOOLS_BRANCH" ]; then echo "CORTX_TOOLS_BRANCH not provided.Exiting..."; exit 1; fi

    rm -rf $SCRIPT_LOCATION
    yum install git -y
    git clone https://$GITHUB_TOKEN@github.com/$CORTX_TOOLS_REPO $SCRIPT_LOCATION
    pushd $SCRIPT_LOCATION
    git checkout $CORTX_TOOLS_BRANCH
    popd
}

function update_setup_confiuration() {
    #Update root password in config.yaml
    sed -i '/CLUSTER_PASS/s/seagate1/'$PRIMARY_CRED'/g' $SCRIPT_LOCATION/performance/PerfPro/roles/benchmark/vars/config.yml
    sed -i -e '/NODES/{n;s/.*/  - 1: '$PRIMARY_NODE'/}' -e '/CLIENTS/{n;s/.*/  - 1: '$CLIENT_NODE'/}' $SCRIPT_LOCATION/performance/PerfPro/roles/benchmark/vars/config.yml
    sed -i '/BUILD_URL/s/\:/: '${BUILD_URL//\//\\/}'/g' $SCRIPT_LOCATION/performance/PerfPro/roles/benchmark/vars/config.yml
}

function execute_perfpro() {
    yum install ansible -y
    pushd $SCRIPT_LOCATION/performance/PerfPro
    ansible-playbook perfpro.yml -i inventories/hosts --extra-vars '{ "EXECUTION_TYPE" : "sanity" ,"REPOSITORY":{"motr":"cortx-motr","rgw":"cortx-rgw"} , "COMMIT_ID": { "main" : "d1234c" , "dev" : "a5678b"},"PR_ID" : "cortx-rgw/1234" , "USER":"Shailesh Vaidya","GID" : "729494" }' -v
    popd
}

function fetch-setup-info() {
    fetch_build_url
    install_yq
    create_endpoint_url
}

function setup-client() {
    if [ -z "$ENDPOINT_URL" ]; then echo "S3 ENDPOINT_URL not provided.Exiting..."; exit 1; fi
    if [ -z "$ACCESS_KEY" ]; then echo "S3 ACCESS_KEY not provided.Exiting..."; exit 1; fi
    if [ -z "$SECRET_KEY" ]; then echo "S3 SECRET_KEY not provided.Exiting..."; exit 1; fi
    install_awscli
    setup_awscli
    run_io_sanity
}

function execute-perf-sanity() {
    if [ -z "$CORTX_TOOLS_REPO" ]; then echo "CORTX_TOOLS_REPO not provided.Using Default Seagate/seagate-tools"; CORTX_TOOLS_REPO="Seagate/seagate-tools" ; fi
    if [ -z "$CORTX_TOOLS_BRANCH" ]; then echo "CORTX_TOOLS_BRANCH not provided.Using Default main."; CORTX_TOOLS_BRANCH="main"; fi
    if [ -z "$PRIMARY_NODE" ]; then echo "PRIMARY_NODE not provided.Exiting..."; exit 1; fi
    if [ -z "$CLIENT_NODE" ]; then echo "CLIENT_NODE not provided.Exiting..."; exit 1; fi
    if [ -z "$PRIMARY_CRED" ]; then echo "PRIMARY_CRED not provided.Exiting..."; exit 1; fi
    if [ -z "$GITHUB_TOKEN" ]; then echo "GITHUB_TOKEN not provided.Exiting..."; exit 1; fi
    if [ -z "$BUILD_URL" ]; then echo "BUILD_URL not provided.Exiting..."; exit 1; fi
    
    clone_segate_tools_repo
    update_setup_confiuration
    execute_perfpro
}



case $ACTION in
    --setup-client)
        setup-client
    ;;
    --fetch-setup-info)
        fetch-setup-info
    ;;
    --execute-perf-sanity)
        execute-perf-sanity
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;
esac
