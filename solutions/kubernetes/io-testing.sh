#!/bin/bash
#
# Copyright (c) 2021 Seagate Technology LLC and/or its Affiliates
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

set -euo pipefail # exit on failures

source /var/tmp/functions.sh

add_separator Starting IO testing.

echo -e "\nCreating bucket:- 'test'\n"
aws s3 mb s3://test
aws s3 ls

echo -e "\nCreating files to upload to 'test' bucket:-\n"
echo "Creating 'file10mb'"
dd if=/dev/zero of=file10mb bs=1M count=10
echo "Creating 'test-obj.bin'"
date > test-obj.bin

echo -e "\nUploading 'file10mb' file to 'test' bucket:-\n"
aws s3 cp file10mb s3://test/file10MB
echo -e "\nUploading 'test-obj.bin' file to 'test' bucket:-\n"
aws s3 cp test-obj.bin s3://test

echo -e "\nList files in 'test' bucket:-\n"
aws s3 ls s3://test

echo -e "\nDownload 'file10MB' as 'file10mbDn' and check diff:-\n"
aws s3 cp s3://test/file10MB file10mbDn
diff file10mb file10mbDn

echo -e "\nRemove all files in 'test' bucket:-\n"
aws s3 rm s3://test --recursive

echo -e "\nRemove 'test' bucket:-\n"
aws s3 rb s3://test

add_separator Successfully passed IO testing.
