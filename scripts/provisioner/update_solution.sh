#!/bin/bash
#
# Copyright (c) 2020 Seagate Technology LLC and/or its Affiliates
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.
# For any questions about this software or licensing,
# please email opensource@seagate.com or cortx-questions@seagate.com.
#

source "$WORKSPACE"/solutions/kubernetes/functions.sh

HOST_FILE=$PWD/hosts
SCRIPT_PATH=/root/cortx-k8s/k8_cortx_cloud
YQ_VERSION=v4.13.3
YQ_BINARY=yq_linux_386

cp "$WORKSPACE"/scripts/provisioner/hosts "$WORKSPACE"/solutions/kubernetes/hosts

function clone_script_repo() {
    pushd /root
        rm -rf cortx-k8s
        git clone ${CORTX_SCRIPTS_REPO} -b ${CORTX_SCRIPTS_BRANCH}
        cp "$WORKSPACE"/scripts/provisioner/hosts "$SCRIPT_PATH"/hosts
    popd
}

function install_yq_module() {
    pip3 show yq && pip3 uninstall yq -y
    wget https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/${YQ_BINARY}.tar.gz -O - | tar xz && mv ${YQ_BINARY} /usr/bin/yq
    if [ -f /usr/local/bin/yq ]; then rm -rf /usr/local/bin/yq; fi
    ln -s /usr/bin/yq /usr/local/bin/yq
}

function add_image_solution_config() {
    pushd "$SCRIPT_PATH"
        image=$CONTROL_IMAGE yq e -i '.solution.images.cortxcontrol = env(image)' "$SCRIPT_PATH"/solution.example.yaml
        image=$DATA_IMAGE yq e -i '.solution.images.cortxdata = env(image)' "$SCRIPT_PATH"/solution.example.yaml
        image=$SERVER_IMAGE yq e -i '.solution.images.cortxserver = env(image)' "$SCRIPT_PATH"/solution.example.yaml
        image=$HA_IMAGE yq e -i '.solution.images.cortxha = env(image)' "$SCRIPT_PATH"/solution.example.yaml
        image=$CONTROL_IMAGE yq e -i '.solution.images.cortxclient = env(image)' "$SCRIPT_PATH"/solution.example.yaml
    popd
}

function add_node_solution_config() {
    add_common_separator "Updating node info in solution.yaml"
    pushd "$SCRIPT_PATH"
        yq e -i "del(.solution.nodes)" "$SCRIPT_PATH"/solution.example.yaml
        count=1
            for node in $(awk -F[,] '{print $1}' < "$HOST_FILE"| cut -d'=' -f2)
                do
                i=$node yq e -i '.solution.nodes.node'$count'.name = env(i)' "$SCRIPT_PATH"/solution.example.yaml
                count=$((count+1))
            done
    popd
}

function update_secret() {
    pushd "$SCRIPT_PATH"
        yq e -i '.solution.secrets.content.kafka_admin_secret = "Seagate@123"' "$SCRIPT_PATH"/solution.example.yaml
        yq e -i '.solution.secrets.content.consul_admin_secret = "Seagate@123"' "$SCRIPT_PATH"/solution.example.yaml
        yq e -i '.solution.secrets.content.common_admin_secret = "Seagate@123"' "$SCRIPT_PATH"/solution.example.yaml
        yq e -i '.solution.secrets.content.s3_auth_admin_secret = "ldapadmin"' "$SCRIPT_PATH"/solution.example.yaml
        yq e -i '.solution.secrets.content.csm_auth_admin_secret = "seagate2"' "$SCRIPT_PATH"/solution.example.yaml
        yq e -i '.solution.secrets.content.csm_mgmt_admin_secret = "Cortxadmin@123"' "$SCRIPT_PATH"/solution.example.yaml
        yq e -i '.solution.images.consul = "cortx-docker.colo.seagate.com/consul/consul:1.12"' "$SCRIPT_PATH"/solution.example.yaml
    popd
}

function add_storage_solution_config() {
    add_common_separator "Updating storage info in solution.yaml"
    pushd "$SCRIPT_PATH"
        yq e -i "del(.solution.storage.cvg2)" "$SCRIPT_PATH"/solution.example.yaml
        yq e -i "del(.solution.storage.cvg1.devices.data.d7)" "$SCRIPT_PATH"/solution.example.yaml
    popd
}

function copy_solution_file() {
    add_common_separator "Copying updated solution.yaml"
    pushd "$SCRIPT_PATH"
        local ALL_NODES=$(awk -F[,] '{print $1}' < "$HOST_FILE"| cut -d'=' -f2)
        for node in $ALL_NODES
        do
            scp -q solution.example.yaml "$node":$SCRIPT_PATH/solution.yaml
        done
    popd
}

clone_script_repo
install_yq_module
validation
generate_rsa_key
nodes_setup
update_secret
add_image_solution_config
add_node_solution_config
add_storage_solution_config
copy_solution_file
