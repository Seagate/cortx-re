#!/bin/bash
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

rm -rf /var/tmp/firstboot-execute.log /etc/kubernetes-backup /var/lib/kubelet-backup /var/tmp/firstboot-execute.log

####check pod status####
function check_hctl_status {
        sleep 60
        SECONDS=0
         # start pods on master nodes
        kubectl taint nodes $(hostname) node-role.kubernetes.io/master- &>> /var/tmp/firstboot-execute.log

        while [[ SECONDS -lt 1200 ]] ; do
            if /usr/bin/kubectl exec -it $(/usr/bin/kubectl get pods | awk '/cortx-data-pod/{print $1; exit}') -c cortx-motr-hax -- hctl status &>> /var/tmp/firstboot-execute.log ; then
                if ! kubectl exec -it $(kubectl get pods | awk '/cortx-data-pod/{print $1; exit}') -c cortx-motr-hax -- hctl status| grep -q -E 'unknown|offline|failed'; then
                        echo "All CORTX pods are up" &>> /var/tmp/firstboot-execute.log
                        kubectl exec -it $(/usr/bin/kubectl get pods | awk '/cortx-data-pod/{print $1; exit}') -c cortx-motr-hax -- hctl status >> /var/tmp/firstboot-execute.log
                        mv /var/tmp/firstboot.sh /var/tmp/first-boot-executed.sh
                        set_s3_instance
                else
                        echo "Pod is not up wait for 30 sec"  &>> /var/tmp/firstboot-execute.log
                        sleep 30
                fi
            else
                echo "----------------------[ hctl status not working yet. Sleeping for 1min.... ]-------------------------" &>> /var/tmp/firstboot-execute.log
                sleep 60
            fi
        done
}

# set s3 server instance entry
function set_s3_instance {

        # set cortx-data pod ip in hosts file
        CORTX_IO_SVC=$(/usr/bin/kubectl get pods -o wide | grep cortx-data-pod | awk '{print $6}' | head -1)
        echo $CORTX_IO_SVC  &>> /var/tmp/firstboot-execute.log
        echo "${CORTX_IO_SVC} s3.seagate.com iam.seagate.com" >> /etc/hosts

        # Remove backup files
        rm -rf /etc/kubernetes-backup &>> /var/tmp/firstboot-execute.log

        exit 0
}

#Stop services
systemctl stop kubelet docker

touch /var/tmp/firstboot-execute.log

echo "setup kubernetes" &>> /var/tmp/firstboot-execute.log

# Backup Kubernetes and kubelet
mv -f /etc/kubernetes /etc/kubernetes-backup
#mv -f /var/lib/kubelet /var/lib/kubelet-backup

# Keep the certs we need
mkdir -p /etc/kubernetes
cp -r /etc/kubernetes-backup/pki /etc/kubernetes
rm -rf /etc/kubernetes/pki/{apiserver.*,etcd/peer.*}

# Start docker
systemctl start docker

kubeadm init --ignore-preflight-errors=DirAvailable--var-lib-etcd &>> /var/tmp/firstboot-execute.log
cp /etc/kubernetes/admin.conf ~/.kube/config

systemctl stop kubelet docker
systemctl start docker kubelet

# Verify resutl
# kubectl cluster-info

# Check Pod status
check_hctl_status
