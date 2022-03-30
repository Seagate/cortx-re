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
source "$WORKSPACE"/solutions/kubernetes/cortx-deploy-functions.sh

HOST_FILE=$PWD/hosts
SCRIPT_PATH=/root/cortx-k8s/k8_cortx_cloud

cp "$WORKSPACE"/scripts/provisioner/hosts "$WORKSPACE"/solutions/kubernetes/hosts

function add_image_solution_config() {
    pushd "$WORKSPACE"/scripts/provisioner
        image=$CONTROL_IMAGE yq e -i '.solution.images.cortxcontrol = env(image)' solution_template.yaml
        image=$DATA_IMAGE yq e -i '.solution.images.cortxdata = env(image)' solution_template.yaml
        image=$SERVER_IMAGE yq e -i '.solution.images.cortxserver = env(image)' solution_template.yaml
        image=$HA_IMAGE yq e -i '.solution.images.cortxha = env(image)' solution_template.yaml
        image=$CONTROL_IMAGE yq e -i '.solution.images.cortxclient = env(image)' solution_template.yaml
    popd
}

function add_node_solution_config() {
    echo "Updating node info in solution.yaml"
    pushd "$WORKSPACE"/scripts/provisioner
        if [ "$(wc -l < "$HOST_FILE")" == "1" ]; then
            local NODE=$(awk -F[,] '{print $1}' < "$HOST_FILE" | cut -d'=' -f2)
            i=$NODE yq e -i '.solution.nodes.node1.name = env(i)' solution_template.yaml
        else
            count=1
            for node in $(awk -F[,] '{print $1}' < "$HOST_FILE"| cut -d'=' -f2)
                do
                i=$node yq e -i '.solution.nodes.node'$count'.name = env(i)' solution_template.yaml
                count=$((count+1))
            done
            sed -i -e 's/- //g' -e '/null/d' solution_template.yaml
        popd
        fi
}

function copy_solution_file() {
    echo "Copying updated solution.yaml"
    pushd "$WORKSPACE"/scripts/provisioner
        local ALL_NODES=$(awk -F[,] '{print $1}' < "$HOST_FILE"| cut -d'=' -f2)
        for node in $ALL_NODES
        do
            scp -q solution_template.yaml "$node":$SCRIPT_PATH/solution.yaml
        done
    popd
}

install_yq
validation
generate_rsa_key
nodes_setup
add_image_solution_config
add_node_solution_config
copy_solution_file
