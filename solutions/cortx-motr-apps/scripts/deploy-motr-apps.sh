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

#Setup required disks on VM using disks.sh
#Configure CDF.yaml by updating data-path nic and disks details in sample CDF.yaml
#Bootstrap cluster on VM
#git clone https://github.com/Seagate/cortx-motr-apps && cd cortx-motr-apps
#./configure
#make
#make vmrcf
#make test

YQ_VERSION=v4.25.1
YQ_BINARY=yq_linux_386
DATA_INTERFACE=$(route -n | awk '$1 == "0.0.0.0" {print $8}' | head -1)

source ../../kubernetes/functions.sh

# Install yq 4.13.3

function install_yq() {
    add_secondary_separator "Installing yq-$YQ_VERSION"
    pip3 show yq && pip3 uninstall yq -y
    wget -q https://github.com/mikefarah/yq/releases/download/${YQ_VERSION}/${YQ_BINARY}.tar.gz -O - | tar xz && mv ${YQ_BINARY} /usr/bin/yq
    if [ -f /usr/local/bin/yq ]; then rm -rf /usr/local/bin/yq; fi    
    ln -s /usr/bin/yq /usr/local/bin/yq
}

function create_disks() {
	add_secondary_separator "Create Disks"
	dest="$HOME/var/motr"
	mkdir -p "$dest" 
	for i in {0..9}; do 
		dd if=/dev/zero of="$dest/disk$i.img" bs=1M seek=9999 count=1 
	done
}

function setup_cluster() {
	add_secondary_separator "Install CORTX Packages"
	yum install cortx-motr cortx-motr-devel cortx-hare -y
	
	add_secondary_separator "Create CDF file from template and update Disk and interface details"
	cp /opt/seagate/cortx/hare/share/cfgen/examples/singlenode.yaml singlenode.yaml
	
	for i in {0..9}; do
		echo $dest/disk$i.img
		sed -i 's|/dev/loop'$i'|'$dest'/disk'$i'.img|' singlenode.yaml
	done        
	
	interface=$DATA_INTERFACE yq e -i '.nodes[0].data_iface = env(interface)' singlenode.yaml

	add_secondary_separator "Bootstrap Cluster"
	hctl bootstrap --mkfs singlenode.yaml

	add_secondary_separator "Print Cluster Status"
	hctl status
}

function install_motr_apps() {
	add_secondary_separator "Install requird package for motr-apps"
	yum install castxml autoconf automake gcc make cmake openssl openssl-devel perl-XML-LibXML -y

	add_secondary_separator "Build and Test motr-apps"
	git clone https://github.com/Seagate/cortx-motr-apps && cd cortx-motr-apps
	./autogen.sh
	./configure
	make
	make vmrcf
	make test
}

function remove_cluster() {
	[[ -f /usr/bin/hctl ]] && hctl shutdown || add_secondary_separator "hctl command not available"
	yum remove -y cortx-py-utils cortx-hare cortx-motr cortx-motr-devel
}


install_yq
add_primary_separator "Remove existing Cluster"
remove_cluster
add_primary_separator "Setup CORTX Cluster"
create_disks
setup_cluster
add_primary_separator "Deploy Motr Apps"
install_motr_apps
