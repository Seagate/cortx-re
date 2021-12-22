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

BUCKET="test-bucket"
FILE1="file10mb"
FILE2="test-obj.bin"

echo -e "\nCreating S3 bucket:- '$BUCKET'\n"
aws s3 mb s3://$BUCKET
aws s3 ls

echo -e "\nCreating files to upload to '$BUCKET' bucket:-"
echo -e "\nCreating '$FILE1'"
dd if=/dev/zero of=$FILE1 bs=1M count=10
echo -e "\nCreating '$FILE2'"
date > $FILE2

echo -e "\nUploading '$FILE1' file to '$BUCKET' bucket:-\n"
aws s3 cp $FILE1 s3://$BUCKET/file10MB
echo -e "\nUploading '$FILE2' file to '$BUCKET' bucket:-\n"
aws s3 cp $FILE2 s3://$BUCKET

echo -e "\nList files in '$BUCKET' bucket:-\n"
aws s3 ls s3://$BUCKET

echo -e "\nDownload '$FILE1' as 'file10mbDn' and check diff:-\n"
aws s3 cp s3://$BUCKET/file10MB file10mbDn
FILE_DIFF=$(diff $FILE1 file10mbDn)

if [[ $FILE_DIFF ]]; then
   echo -e "\nDIFF Status: $FILE_DIFF"
else
   echo -e "\nDIFF Status: The files $FILE1 and file10mbDn are similar."
fi


echo -e "\nRemove all files in '$BUCKET' bucket:-\n"
aws s3 rm s3://$BUCKET --recursive

echo -e "\nRemove '$BUCKET' bucket:-\n"
aws s3 rb s3://$BUCKET

add_separator Successfully passed IO testing.
