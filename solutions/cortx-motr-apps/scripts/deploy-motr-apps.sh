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

function create_disks() {

#!/bin/bash 
dest="$HOME/var/motr"
mkdir -p "$dest" 
for i in {0..9}; do 
	dd if=/dev/zero of="$dest/disk$i.img" bs=1M seek=9999 count=1 
done

}


