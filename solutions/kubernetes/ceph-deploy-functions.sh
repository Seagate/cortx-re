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

source /var/tmp/functions.sh

CEPH_NODES=$(cat "$HOST_FILE" | grep -v "$PRIMARY_NODE" | awk -F[,] '{print $1}' | cut -d'=' -f2) || true

function usage() {
    cat << HEREDOC
Usage : $0 [--deploy-mon, --deploy-mgr, --deploy-osd, --deploy-mds]
where,
    --deploy-mon - Deploy Ceph Monitor.
    --deploy-mgr - Deploy Ceph Manager.
    --deploy-osd - Deploy Ceph OSD.
    --deploy-mds - Deploy Ceph Metadata Service.
HEREDOC
}

ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

function install_prereq() {
    add_secondary_separator "Installing Ceph Dependencies on $HOSTNAME"
    yum install https://dl.fedoraproject.org/pub/epel/epel-release-latest-8.noarch.rpm -y && \
    yum install -y dnf-plugins-core  && \
    yum copr enable -y tchaikov/python-scikit-learn  && \
    yum copr enable -y tchaikov/python3-asyncssh  && \
    dnf config-manager --set-enabled powertools resilientstorage && \
    yum install -y --setopt=install_weak_deps=False resource-agents python3-natsort binutils sharutils s3cmd logrotate python3-pyyaml sqlite-devel which openssh-server xfsprogs parted cryptsetup xmlstarlet socat jq selinux-policy-base policycoreutils  \
        libselinux-utils sudo mailcap python3-jsonpatch python3-kubernetes python3-requests python3-werkzeug python3-pyOpenSSL python3-pecan python3-bcrypt python3-cherrypy python3-routes python3-jwt python3-jinja2 ca-certificates \
        python3-asyncssh openssh-clients fuse python3-prettytable psmisc librdmacm libbabeltrace librabbitmq librdkafka liboath lttng-tools lttng-ust libicu thrift wget unzip util-linux python3-setuptools udev device-mapper \
        e2fsprogs python3-saml kmod lvm2 gdisk smartmontools nvme-cli libstoragemgmt systemd-udev procps-ng hostname python3-rtslib attr python3-scikit-learn gperftools
}

function install_ceph() {
    add_secondary_separator "Installing Ceph Packages on $HOSTNAME"
    pushd /root/RPMS
        mv noarch/*.rpm . && mv x86_64/*.rpm . && rmdir noarch/ x86_64/
        rpm -ivh *.rpm
    popd
}

function deploy_prereq() {
    validation
    generate_rsa_key
    nodes_setup
}

function deploy_mon() {
    add_secondary_separator "Create ceph.conf"
    FSID=$(uuidgen)
    cat << EOF > /etc/ceph/ceph.conf
[global]
fsid = $FSID
mon initial members = $(hostname -s)
mon host = $(hostname -i)
EOF
    add_secondary_separator "Create keyrings"
    ceph-authtool --create-keyring /tmp/ceph.mon.keyring --gen-key -n mon. --cap mon 'allow *'
    ceph-authtool --create-keyring /etc/ceph/ceph.client.admin.keyring --gen-key -n client.admin --cap mon 'allow *' --cap osd 'allow *' --cap mds 'allow *' --cap mgr 'allow *'
    ceph-authtool --create-keyring /var/lib/ceph/bootstrap-osd/ceph.keyring --gen-key -n client.bootstrap-osd --cap mon 'profile bootstrap-osd' --cap mgr 'allow r'
    ceph-authtool /tmp/ceph.mon.keyring --import-keyring /etc/ceph/ceph.client.admin.keyring
    ceph-authtool /tmp/ceph.mon.keyring --import-keyring /var/lib/ceph/bootstrap-osd/ceph.keyring
    chown ceph:ceph /tmp/ceph.mon.keyring

    add_secondary_separator "Monmap config"
    monmaptool --create --add $(hostname -s) $(hostname -i) --fsid $FSID /tmp/monmap
    sudo -u ceph mkdir /var/lib/ceph/mon/ceph-$(hostname -s)
    sudo -u ceph ceph-mon --mkfs -i $(hostname -s) --monmap /tmp/monmap --keyring /tmp/ceph.mon.keyring

    add_secondary_separator "Update ceph.conf"
    cat << EOF >> /etc/ceph/ceph.conf
public network = $(ip -o -f inet addr show | awk '/scope global/ {print $4}' | head -1)
auth cluster required = cephx
auth service required = cephx
auth client required = cephx
osd journal size = 10000
osd pool default size = $(echo "$OSD_POOL_DEFAULT_SIZE")
osd pool default min size = $(echo "$OSD_POOL_DEFAULT_MIN_SIZE")
osd pool default pg num = $(echo "$OSD_POOL_DEFAULT_PG_NUM")
osd pool default pgp num = $(echo "$OSD_POOL_DEFAULT_PGP_NUM")
osd crush chooseleaf type = 1
mon allow pool delete = true
EOF

    add_secondary_separator "Start Ceph Monitor"
    systemctl start ceph-mon@$(hostname -s)
    sleep 5
    ceph mon enable-msgr2
    sleep 5
}

function ceph_status() {
    ceph -s
}

function deploy_dashboard() {
    add_secondary_separator "Setup Ceph Dashboard"
    ceph config set mgr mgr/dashboard/ssl false
    ceph mgr module enable dashboard
    echo "cephadmin" > ceph-passwd

    add_common_separator "Create dashbaord user"
    ceph dashboard ac-user-create admin -i ceph-passwd administrator

    ceph mgr services
    echo "Dashboard creds: admin/cephadmin"
}

function deploy_mgr() {
    add_secondary_separator "Setup Ceph Monitor"
    mkdir -p /var/lib/ceph/mgr/ceph-foo
    ceph auth get-or-create mgr.foo mon 'allow profile mgr' osd 'allow *' mds 'allow *' > /var/lib/ceph/mgr/ceph-foo/keyring
    ceph-mgr -i foo

    deploy_dashboard
}

function deploy_osd() {
    add_secondary_separator "Copy config files"
    scp_ceph_node "/etc/ceph" "/etc/ceph/ceph.conf"
    scp_ceph_node "/var/lib/ceph/bootstrap-osd/" "/var/lib/ceph/bootstrap-osd/ceph.keyring"
    scp_ceph_node "/etc/ceph/" "/var/lib/ceph/bootstrap-osd/ceph.keyring"

    add_secondary_separator "Setup OSD"
    for disks in $(cat "$OSD_DISKS")
    do
        ssh_ceph_node "ceph-volume lvm create --data $disks"
    done
}

function deploy_mds() {
    add_secondary_separator "Copy keyring"
    scp_ceph_node "/etc/ceph" "/etc/ceph/ceph.client.admin.keyring"

    add_secondary_separator "Setup MDS"
    mkdir -p /var/lib/ceph/mds/ceph-$(hostname -s)
    ceph-authtool --create-keyring /var/lib/ceph/mds/ceph-$(hostname -s)/keyring --gen-key -n mds.$(hostname -s)
    ceph auth add mds.$(hostname -s) osd "allow rwx" mds "allow *" mon "allow profile mds" -i /var/lib/ceph/mds/ceph-$(hostname -s)/keyring

    cat << EOF >> /etc/ceph/ceph.conf

[mds.$(hostname -s)]
host = $(hostname -s)
keyring = /var/lib/ceph/mds/ceph-$(hostname -s)/keyring
EOF

    chmod o+r /var/lib/ceph/mds/ceph-ssc-vm-g4-rhev4-1395/keyring
    chmod g+r /var/lib/ceph/mds/ceph-ssc-vm-g4-rhev4-1395/keyring

    add_secondary_separator "Start Ceph MDS"
    systemctl start ceph-mds@$(hostname -s)

    add_secondary_separator "MDS Status"
    ceph mds stat
}

function deploy_fs() {

}

function deploy_rgw() {

}

case $ACTION in
    --install-prereq)
        install_prereq
    ;;
    --install-ceph)
        install_ceph
    ;;
    --deploy-prereq)
        deploy_prereq
    ;;
    --status)
        ceph_status
    ;;
    --deploy-mon)
        deploy_mon
    ;;
    --deploy-mgr)
        deploy_mgr
    ;;
    --deploy-osd)
        deploy_osd
    ;;
    --deploy-mds)
        deploy_mds
    ;;
    --deploy-fs)
        deploy_fs
    ;;
    --deploy-rgw)
        deploy_rgw
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;    
esac