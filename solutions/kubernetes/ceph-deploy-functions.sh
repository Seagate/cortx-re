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
source /etc/os-release

HOST_FILE=/var/tmp/hosts
ALL_NODES=$(cat "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
CEPH_NODES=$(cat "$HOST_FILE" | grep -v "$PRIMARY_NODE" | awk -F[,] '{print $1}' | cut -d'=' -f2)

function usage() {
    cat << HEREDOC
Usage : $0 [--install-pereq, --install-ceph, --deploy-prereq, --deploy-mon, --deploy-mgr, --deploy-osd, --deploy-mds, --deploy-fs, --deploy-rgw, --io-operation, --status]
where,
    --install-prereq - Install Ceph Dependencies before installing ceph packages.
    --install-ceph - Install Ceph Packages.
    --deploy-prereq - Prerquisites before Ceph Deployemnt on cluster nodes required for passwordless ssh to copy ceph configuration and keys.
    --deploy-mon - Deploy Ceph Monitor daemon on primary node.
    --deploy-mgr - Deploy Ceph Manager daemon on primary node.
    --deploy-osd - Deploy Ceph OSD daemon on all nodes.
    --deploy-mds - Deploy Ceph Metadata Service daemon on primary node.
    --deploy-fs - Deploy Ceph FS daemon on primary node.
    --deploy-rgw - Deploy Ceph Rados Gateway daemon on primary node.
    --io-operation - Perform IO operation.
    --status - Show Ceph Cluster Status.
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

    if [[ "$(df -BG  / | awk '{ print $4 }' | tail -n 1 | sed 's/G//')" < "30" ]]; then
        add_secondary_separator "Root partition doesn't have sufficient disk space"
        exit 1
    fi

    case "$ID" in
        rocky)
            systemctl stop firewalld && systemctl disable firewalld && sudo systemctl mask --now firewalld
            setenforce 0
            sed -i  -e 's/SELINUX=enforcing/SELINUX=disabled/g' -e 's/SELINUX=enforcing/SELINUX=permissive/g' /etc/sysconfig/selinux
            yum install https://dl.fedoraproject.org/pub/epel/epel-release-latest-8.noarch.rpm -y && \
            yum install -y dnf-plugins-core  && \
            yum copr enable -y tchaikov/python-scikit-learn  && \
            yum copr enable -y tchaikov/python3-asyncssh
            dnf config-manager --set-enabled powertools resilientstorage
            yum install -y --setopt=install_weak_deps=False resource-agents python3-natsort binutils sharutils s3cmd logrotate python3-pyyaml sqlite-devel which openssh-server xfsprogs parted cryptsetup xmlstarlet socat jq selinux-policy-base policycoreutils  \
                libselinux-utils sudo mailcap python3-jsonpatch python3-kubernetes python3-requests python3-werkzeug python3-pyOpenSSL python3-pecan python3-bcrypt python3-cherrypy python3-routes python3-jwt python3-jinja2 ca-certificates \
                python3-asyncssh openssh-clients fuse python3-prettytable psmisc librdmacm libbabeltrace librabbitmq librdkafka liboath lttng-tools lttng-ust libicu thrift wget unzip util-linux python3-setuptools udev device-mapper \
                e2fsprogs python3-saml kmod lvm2 gdisk smartmontools nvme-cli libstoragemgmt systemd-udev procps-ng hostname python3-rtslib attr python3-scikit-learn gperftools
        ;;
        centos)
            yum remove epel-next-release-8-13.el8.noarch -y && dnf install https://dl.fedoraproject.org/pub/epel/epel-release-latest-8.noarch.rpm -y && \
            yum install epel-release -y && \
            yum install -y dnf-plugins-core  && \
            yum copr enable -y tchaikov/python-scikit-learn  && \
            yum copr enable -y tchaikov/python3-asyncssh  && \
            yum install -y --setopt=install_weak_deps=False sharutils s3cmd logrotate python3-pyyaml sqlite-devel which openssh-server xfsprogs parted cryptsetup xmlstarlet socat jq selinux-policy-base policycoreutils  \
                libselinux-utils sudo mailcap python3-jsonpatch python3-kubernetes python3-requests python3-werkzeug python3-pyOpenSSL python3-pecan python3-bcrypt python3-cherrypy python3-routes python3-jwt python3-jinja2 ca-certificates \
                python3-asyncssh openssh-clients fuse python3-prettytable psmisc librdmacm libbabeltrace librabbitmq librdkafka liboath lttng-tools lttng-ust libicu thrift wget unzip util-linux python3-setuptools udev device-mapper \
                e2fsprogs python3-saml kmod lvm2 gdisk smartmontools nvme-cli libstoragemgmt systemd-udev procps-ng hostname python3-rtslib attr python3-scikit-learn gperftools && \
                yum install python3-natsort binutils -y
            rpm -ivh http://mirror.centos.org/centos/8-stream/HighAvailability/x86_64/os/Packages/resource-agents-4.1.1-97.el8.x86_64.rpm
        ;;
        ubuntu)
            pushd /root/RPMS # subject to change until binaries are fetched from a central repo
                dpkg -i *.deb     # this command will throw errors which is expected as it collects required dependencies for the installation
                apt-get -f install -y
            popd
        ;;
    esac
}

function install_ceph() {
    add_secondary_separator "Installing Ceph Packages on $HOSTNAME"

    case "$ID" in
        rocky)
            pushd /root/RPMS # subject to change until binaries are fetched from a central repo
                mv noarch/*.rpm . && mv x86_64/*.rpm . && rmdir noarch/ x86_64/
                rpm -ivh *.rpm
            popd
        ;;
        centos)
            pushd /root/RPMS # subject to change until binaries are fetched from a central repo
                mv noarch/*.rpm . && mv x86_64/*.rpm . && rmdir noarch/ x86_64/
                rpm -ivh *.rpm
            popd
        ;;
        ubuntu)
            echo "All pacakges are installed in install_prereq step only."
        ;;
    esac
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
mon allow pool delete = true
EOF

    add_secondary_separator "Start Ceph Monitor"
    systemctl start ceph-mon@$(hostname -s)
    sleep 10
    ceph mon enable-msgr2
    sleep 10
}

function ceph_status() {
    add_primary_separator "Ceph Cluster Status"
    ceph -s
    ceph health detail
 
    add_secondary_separator "Ceph mgr active modules"
    ceph mgr module ls

    add_secondary_separator "Ceph OSD details"
    ceph osd tree
    ceph osd df

    add_secondary_separator "Ceph FS details"
    ceph fs ls
    rados lspools
    ceph osd pool ls detail
    ceph mds stat

    add_secondary_separator "Running Ceph Frontend Services"
    ceph mgr services
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

    add_secondary_separator "Disable 'mons are allowing insecure global_id reclaim' health warning"
    ceph config set mon mon_warn_on_insecure_global_id_reclaim false
    ceph config set mon mon_warn_on_insecure_global_id_reclaim_allowed false
}

function deploy_mgr() {
    add_secondary_separator "Setup Ceph Monitor"
    mkdir -p /var/lib/ceph/mgr/ceph-foo
    ceph auth get-or-create mgr.foo mon 'allow profile mgr' osd 'allow *' mds 'allow *' > /var/lib/ceph/mgr/ceph-foo/keyring
    ceph-mgr -i foo

    sleep 30
    deploy_dashboard
}

function deploy_osd() {
    add_secondary_separator "Copy config files"
    scp_ceph_nodes "/etc/ceph" "/etc/ceph/ceph.conf"
    scp_ceph_nodes "/var/lib/ceph/bootstrap-osd/" "/var/lib/ceph/bootstrap-osd/ceph.keyring"
    scp_ceph_nodes "/etc/ceph/" "/var/lib/ceph/bootstrap-osd/ceph.keyring"
    cp /var/lib/ceph/bootstrap-osd/ceph.keyring /etc/ceph

    add_secondary_separator "Setup OSD"
    echo "OSD Disks: $(cat $OSD_DISKS)"
    for disks in $(cat $OSD_DISKS)
        do
            ssh_all_nodes "ceph-volume lvm create --data $disks"
        done

    add_secondary_separator "Ceph OSD status"
    ceph osd tree

    add_secondary_separator "Ceph OSD details"
    ceph osd df
}

function deploy_mds() {
    add_secondary_separator "Copy keyring"
    scp_ceph_nodes "/etc/ceph" "/etc/ceph/ceph.client.admin.keyring"

    add_secondary_separator "Setup MDS"
    mkdir -p /var/lib/ceph/mds/ceph-$(hostname -s)
    ceph-authtool --create-keyring /var/lib/ceph/mds/ceph-$(hostname -s)/keyring --gen-key -n mds.$(hostname -s)
    ceph auth add mds.$(hostname -s) osd "allow rwx" mds "allow *" mon "allow profile mds" -i /var/lib/ceph/mds/ceph-$(hostname -s)/keyring

    cat << EOF >> /etc/ceph/ceph.conf

[mds.$(hostname -s)]
host = $(hostname -s)
keyring = /var/lib/ceph/mds/ceph-$(hostname -s)/keyring
EOF

    chmod o+r /var/lib/ceph/mds/ceph-$(hostname -s)/keyring
    chmod g+r /var/lib/ceph/mds/ceph-$(hostname -s)/keyring

    add_secondary_separator "Start Ceph MDS"
    systemctl start ceph-mds@$(hostname -s)

    sleep 10
    add_secondary_separator "MDS Status"
    ceph mds stat

    ceph_status
}

function deploy_fs() {
    ceph osd pool create cephfs_data 1
    ceph osd pool create cephfs_metadata 1
    ceph fs new cephfs cephfs_metadata cephfs_data

    ceph_status
}

function deploy_rgw() {
    mkdir -p /var/lib/ceph/radosgw/ceph-rgw.$(hostname -s)
    ceph auth get-or-create client.rgw.$(hostname -s) osd 'allow rwx' mon 'allow rw' -o /var/lib/ceph/radosgw/ceph-rgw.$(hostname -s)/keyring
    cat << EOF >> /etc/ceph/ceph.conf

[client.rgw.$(hostname -s)]
host = $(hostname -s)
keyring = /var/lib/ceph/radosgw/ceph-rgw.$(hostname -s)/keyring
log file = /var/log/ceph/ceph-rgw-$(hostname -s).log
rgw frontends = "beast endpoint=$(hostname -i):9999"
rgw thread pool size = 512
EOF

    scp_ceph_nodes "/etc/ceph" "/etc/ceph/ceph.conf"
    systemctl start ceph-radosgw@rgw.$(hostname -s)
    systemctl enable ceph-radosgw@rgw.$(hostname -s)

    ceph_status
}

function io_operation() {
    echo "empty"
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
    --io-operation)
        io_operation
    ;;
    --status)
        ceph_status
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;    
esac