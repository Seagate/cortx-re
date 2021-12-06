#!/usr/bin/python
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

import subprocess

dev_list = subprocess.Popen(["multipath -ll|grep mpath |sort -k2|cut -d' ' -f1|sed 's|mpath|/dev/disk/by-id/dm-name-mpath|g'"], shell=True, stdout=subprocess.PIPE).stdout
split_dev = dev_list.read().splitlines()
length = len(split_dev)
middle_index = length//2
second_half = split_dev[middle_index:]
cvg1_meta_list = second_half[7:8]
cvg1_meta_dev = ",".join(cvg1_meta_list)
print(cvg1_meta_dev)
