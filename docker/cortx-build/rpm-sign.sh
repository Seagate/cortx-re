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

if [ "x$1" = "x" ]; then
        cat /opt/rpm-signing/gpgoptions >>  ~/.rpmmacros
        sed -i '/passphrase/d' /opt/rpm-signing/genkey-batch
        sed -i -e 's/$argv 1/$argv 0/g' -e 's/${PASSPHRASE}/ /g' -e '/PASSPHRASE/d' /opt/rpm-signing/rpm-sign.sh
        /usr/bin/gpg --pinentry-mode loopback --passphrase '' --batch --gen-key /opt/rpm-signing/genkey-batch
        /usr/bin/gpg --export -a 'Seagate'  > /opt/rpm-signing/RPM-GPG-KEY-Seagate
        rpm --import /opt/rpm-signing/RPM-GPG-KEY-Seagate
elif [ "$1" = "rockylinux" ]; then
	sed "s/--passphrase-fd 3 //g" /opt/rpm-signing/gpgoptions > /opt/rpm-signing/gpgoptions_rocky
	sed -i -e "s/Passphrase:.*/Passphrase: seagate/g" /opt/rpm-signing/genkey-batch
        cat /opt/rpm-signing/gpgoptions_rocky >> ~/.rpmmacros
        gpg --batch --gen-key /opt/rpm-signing/genkey-batch
        gpg --export -a 'Seagate'  > /opt/rpm-signing/RPM-GPG-KEY-Seagate
        rpm --import /opt/rpm-signing/RPM-GPG-KEY-Seagate
fi

