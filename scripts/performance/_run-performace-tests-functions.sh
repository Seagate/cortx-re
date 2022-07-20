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
ANIBLE_LOG_FILE="/var/tmp/perf_sanity_run.log"
PERF_STATS_FILE="/var/tmp/perf_sanity_stats.txt"
SSH_KEY_FILE=/root/.ssh/id_rsa

function usage() {
    cat << HEREDOC
Usage : $0 [--setup-client, --fetch-setup-info]
where,
    --setup-client - Setup S3 Client.
    --fetch-setup-info - Fetch setup information for PerfPro.
    --execute-perf-sanity - Execute Performance sanity script.
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
    SECRET_KEY=$(kubectl get secrets/cortx-secret  --template={{.data.s3_auth_admin_secret}} | base64 -d)
    HTTP_PORT=$(kubectl get svc cortx-io-svc-0 -o=jsonpath='{.spec.ports[?(@.port==80)].nodePort}')
    if [ $(systemd-detect-virt -v) == "none" ];then
        CLUSTER_TYPE=HW 
	    IP_ADDRESS=$(ifconfig eno5 | grep inet -w | awk '{print $2}')
    else
        CLUSTER_TYPE=VM		
	    IP_ADDRESS=$(ifconfig eth1 | grep inet -w | awk '{print $2}')
    fi 
    ENDPOINT_URL="http://$IP_ADDRESS:$HTTP_PORT"

    echo ENDPOINT_URL=$ENDPOINT_URL
    echo ACCESS_KEY=$ACCESS_KEY
    echo SECRET_KEY=$SECRET_KEY
    echo BUILD_URL=$BUILD_URL
    echo CLUSTER_TYPE=$CLUSTER_TYPE
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

    if [ $CLUSTER_TYPE == VM ]; then
	sed -i -e 's/00/0/g' -e 's/450/45/g'  /root/performance-scripts/performance/PerfPro/roles/benchmark/vars/s3config.yml    
    fi
}

function execute_perfpro() {
    yum install ansible -y
    pushd $SCRIPT_LOCATION/performance/PerfPro
        add_primary_separator "Executing Ansible CLI"
        ANSIBLE_LOG_PATH=$ANIBLE_LOG_FILE ansible-playbook perfpro.yml -i inventories/hosts --extra-vars "{ \"EXECUTION_TYPE\" : \"sanity\" ,\"REPOSITORY\":[{ \"category\": \"motr\", \"repo\": \"cortx-motr\", \"branch\": \"k8s\", \"commit\": \"a1234b\" }, { \"category\": \"rgw\", \"repo\": \"cortx-rgw\", \"branch\": \"dev\", \"commit\": \"c5678d\" }, { \"category\": \"hare\", \"repo\": \"cortx-hare\", \"branch\": \"main\", \"commit\": \"e9876f\" }],\"PR_ID\" : \"cortx-rgw/1234\" , \"USER\":\"Username\",\"GID\" : \"JENKINS\", \"NODES\":{\"1\": \"$PRIMARY_NODE\"} , \"CLIENTS\":{\"1\": \"$CLIENT_NODE\"} , \"main\":{\"db_server\": \"$DB_SERVER\", \"db_port\": \"$DB_PORT\", \"db_name\": \"$DB_NAME\", \"db_user\": \"$DB_USER\", \"db_passwd\": \"$DB_PASSWD\", \"$DB_DATABASE\": \"performance_database\", \"db_url\": \"mongodb://$DB_USER:$DB_PASSWD@$DB_SERVER:$DB_PORT/\"}, \"config\":{\"CLUSTER_PASS\": \"$PRIMARY_CRED\", \"END_POINTS\": \"$ENDPOINT_URL\", \"CUSTOM\" : \"VM\" }}" -v
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
    rm -f $ANIBLE_LOG_FILE $PERF_STATS_FILE
    install_awscli
    setup_awscli
    run_io_sanity
}

function execute-perf-sanity() {
    if [ -z "$CORTX_TOOLS_REPO" ]; then echo "CORTX_TOOLS_REPO not provided.Using Default Seagate/seagate-tools"; CORTX_TOOLS_REPO="Seagate/seagate-tools" ; fi
    if [ -z "$CORTX_TOOLS_BRANCH" ]; then echo "CORTX_TOOLS_BRANCH not provided.Using Default main."; CORTX_TOOLS_BRANCH="main"; fi
    if [ -z "$PRIMARY_NODE" ]; then echo "ERROR:PRIMARY_NODE not provided.Exiting..."; exit 1; fi
    if [ -z "$CLIENT_NODE" ]; then echo "ERROR:CLIENT_NODE not provided.Exiting..."; exit 1; fi
    if [ -z "$PRIMARY_CRED" ]; then echo "ERROR:PRIMARY_CRED not provided.Exiting..."; exit 1; fi
    if [ -z "$GITHUB_TOKEN" ]; then echo "ERROR:GITHUB_TOKEN not provided.Exiting..."; exit 1; fi
    if [ -z "$BUILD_URL" ]; then echo "ERROR:BUILD_URL not provided.Exiting..."; exit 1; fi
    
    generate_rsa_key
    sed -i '/'$PRIMARY_NODE'/d' /root/.ssh/known_hosts
    passwordless_ssh "$PRIMARY_NODE" "root" "$PRIMARY_CRED"
    passwordless_ssh "$CLIENT_NODE" "root" "$CLIENT_CRED"
    clone_segate_tools_repo
    update_setup_confiuration
    execute_perfpro
    generate_perf_stats
}

function generate_perf_stats() {
   add_secondary_separator "CORTX Cluster details" | tee -a $PERF_STATS_FILE
   echo "CLUSTER INFRASTRUCTURE: $CLUSTER_TYPE" | tee -a $PERF_STATS_FILE
   ssh $PRIMARY_NODE "kubectl get nodes --no-headers | awk '{print $1}'" | tee -a $PERF_STATS_FILE
   add_secondary_separator "CORTX Image details" | tee -a $PERF_STATS_FILE
   ssh $PRIMARY_NODE "kubectl get pods -o jsonpath="{.items[*].spec.containers[*].image}" | tr ' ' '\n' | sort | uniq" | tee -a $PERF_STATS_FILE
   #Fetch info from Ansible logs
   add_secondary_separator "Performance Stats" | tee -a $PERF_STATS_FILE
   grep -i '\[S3Bench\] Running' $ANIBLE_LOG_FILE | grep -vi TASK | sed -e 's/-//g' -e 's/^ //g' -e 's/*//g' | cut -d':' -f4 | sed 's/^ //g' | sort -n | tee -a $PERF_STATS_FILE
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
