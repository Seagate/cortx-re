#!/usr/bin/expect -d
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

set PASSPHRASE [lindex $argv 0]
set FILE [lindex $argv 1]

spawn gpg --output ${FILE}.sig  --detach-sig ${FILE}
expect -exact "\r\nYou need a passphrase to unlock the secret key for\r\nuser: "
send -- "${PASSPHRASE}\r"
expect eof