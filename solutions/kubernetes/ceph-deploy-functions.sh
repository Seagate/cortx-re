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

SCRIPTS_LOCATION=/var/tmp
HOST_FILE=/var/tmp/hosts
ALL_NODES=$(cat "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
CEPH_NODES=$(cat "$HOST_FILE" | grep -v "$PRIMARY_NODE" | awk -F[,] '{print $1}' | cut -d'=' -f2)

function usage() {
    cat << HEREDOC
Usage : $0 [--install-pereq, --install-ceph, --deploy-prereq, --deploy-mon, --deploy-mgr, --deploy-osd, --deploy-mds, --deploy-fs, --deploy-rgw, --prereq-ceph-docker, --deploy-ceph-docker, --io-operation, --status, --destroy-cluster-vm, --destroy-cluster-docker]
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
    --prereq-ceph-docker - Setup prerequisites for Ceph docker deployment.
    --deploy-ceph-docker - Deploy Ceph in docker.
    --io-operation - Perform IO operation.
    --status - Show Ceph Cluster Status.
    --destroy-cluster-vm - Destroy Ceph cluster deployed on VMs.
    --destroy-cluster-docker - Destroy Ceph cluster deployed on Docker.
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

    if [[ "$(df -BG  / | awk '{ print $4 }' | tail -n 1 | sed 's/G//')" -lt "30" ]]; then
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
            #echo "deb [trusted=yes] http://cortx-storage.colo.seagate.com/releases/ceph/ceph/ubuntu-20.04/quincy/last_successful/ amd64/" > /etc/apt/sources.list.d/ceph-cortx-storage.list
            #apt update

            add_common_separator "Currently mounting bigstorage to nodes and installing packages until ubuntu repo is setup."
            mkdir -p /mnt/bigstorage/releases/ceph
            mount -t nfs4 cortx-storage.colo.seagate.com:/mnt/data1/releases/ceph /mnt/bigstorage/releases/ceph/
        ;;
    esac
}

function install_ceph() {
    add_secondary_separator "Installing Ceph Packages on $HOSTNAME"

    case "$ID" in
        rocky)
            cat << EOF > /etc/yum.repos.d/ceph.repo
[Ceph]
name=Ceph Packages
baseurl=http://cortx-storage.colo.seagate.com/releases/ceph/ceph/rockylinux-8.4/quincy/last_successful/
gpgcheck=0
enabled=1
EOF
            yum repolist
            yum install -y cephadm cephfs-top ceph-grafana-dashboards ceph-mgr-cephadm ceph-mgr-dashboard ceph-mgr-diskprediction-local ceph-mgr-k8sevents ceph-mgr-modules-core \
                ceph-mgr-rook ceph-prometheus-alerts ceph-resource-agents ceph-volume ceph ceph-base ceph-base-debuginfo ceph-common ceph-common-debuginfo \
                ceph-debuginfo ceph-debugsource cephfs-mirror cephfs-mirror-debuginfo ceph-fuse ceph-fuse-debuginfo ceph-immutable-object-cache \
                ceph-immutable-object-cache-debuginfo ceph-mds ceph-mds-debuginfo ceph-mgr ceph-mgr-debuginfo ceph-mon ceph-mon-debuginfo \
                ceph-osd ceph-osd-debuginfo ceph-radosgw ceph-radosgw-debuginfo ceph-selinux ceph-test ceph-test-debuginfo libcephfs2 \
                libcephfs2-debuginfo libcephfs-devel libcephsqlite libcephsqlite-debuginfo libcephsqlite-devel librados2 librados2-debuginfo \
                librados-devel librados-devel-debuginfo libradospp-devel libradosstriper1 libradosstriper1-debuginfo libradosstriper-devel librbd1 librbd1-debuginfo \
                librbd-devel librgw2 librgw2-debuginfo librgw-devel python3-ceph-argparse python3-ceph-common python3-cephfs python3-cephfs-debuginfo \
                python3-rados python3-rados-debuginfo python3-rbd python3-rbd-debuginfo python3-rgw python3-rgw-debuginfo rados-objclass-devel rbd-fuse \
                rbd-fuse-debuginfo rbd-mirror rbd-mirror-debuginfo rbd-nbd rbd-nbd-debuginfo
        ;;
        centos)
            cat << EOF > /etc/yum.repos.d/ceph.repo
[Ceph]
name=Ceph Packages
baseurl=http://cortx-storage.colo.seagate.com/releases/ceph/ceph/centos-8/quincy/last_successful/
gpgcheck=0
enabled=1
EOF
            yum repolist
            yum install -y cephadm cephfs-top ceph-grafana-dashboards ceph-mgr-cephadm ceph-mgr-dashboard ceph-mgr-diskprediction-local ceph-mgr-k8sevents ceph-mgr-modules-core \
                ceph-mgr-rook ceph-prometheus-alerts ceph-resource-agents ceph-volume ceph ceph-base ceph-base-debuginfo ceph-common ceph-common-debuginfo \
                ceph-debuginfo ceph-debugsource cephfs-mirror cephfs-mirror-debuginfo ceph-fuse ceph-fuse-debuginfo ceph-immutable-object-cache \
                ceph-immutable-object-cache-debuginfo ceph-mds ceph-mds-debuginfo ceph-mgr ceph-mgr-debuginfo ceph-mon ceph-mon-debuginfo \
                ceph-osd ceph-osd-debuginfo ceph-radosgw ceph-radosgw-debuginfo ceph-selinux ceph-test ceph-test-debuginfo libcephfs2 \
                libcephfs2-debuginfo libcephfs-devel libcephsqlite libcephsqlite-debuginfo libcephsqlite-devel librados2 librados2-debuginfo \
                librados-devel librados-devel-debuginfo libradospp-devel libradosstriper1 libradosstriper1-debuginfo libradosstriper-devel librbd1 librbd1-debuginfo \
                librbd-devel librgw2 librgw2-debuginfo librgw-devel python3-ceph-argparse python3-ceph-common python3-cephfs python3-cephfs-debuginfo \
                python3-rados python3-rados-debuginfo python3-rbd python3-rbd-debuginfo python3-rgw python3-rgw-debuginfo rados-objclass-devel rbd-fuse \
                rbd-fuse-debuginfo rbd-mirror rbd-mirror-debuginfo rbd-nbd rbd-nbd-debuginfo
        ;;
        ubuntu)
            # apt install -y ceph ceph-base ceph-base-dbg ceph-common ceph-common-dbg ceph-fuse ceph-fuse-dbg ceph-grafana-dashboards ceph-immutable-object-cache \
            #    ceph-immutable-object-cache-dbg ceph-mds ceph-mds-dbg ceph-mgr ceph-mgr-cephadm ceph-mgr-dashboard ceph-mgr-dbg ceph-mgr-diskprediction-local \
            #    ceph-mgr-k8sevents ceph-mgr-modules-core ceph-mgr-rook ceph-mon ceph-mon-dbg ceph-osd ceph-osd-dbg ceph-prometheus-alerts ceph-resource-agents \
            #    ceph-test ceph-test-dbg ceph-volume cephadm cephfs-mirror cephfs-mirror-dbg cephfs-shell cephfs-top libcephfs-dev libcephfs-java libcephfs-jni \
            #    libcephfs2 libcephfs2-dbg librados-dev librados2 librados2-dbg libradospp-dev libradosstriper-dev libradosstriper1 libradosstriper1-dbg librbd-dev \
            #    librbd1 librbd1-dbg librgw-dev librgw2 librgw2-dbg libsqlite3-mod-ceph libsqlite3-mod-ceph-dbg libsqlite3-mod-ceph-dev python3-ceph python3-ceph-argparse \
            #    python3-ceph-common python3-cephfs python3-cephfs-dbg python3-rados python3-rados-dbg python3-rbd python3-rbd-dbg python3-rgw python3-rgw-dbg rados-objclass-dev \
            #    radosgw radosgw-dbg rbd-fuse rbd-fuse-dbg rbd-mirror rbd-mirror-dbg rbd-nbd rbd-nbd-dbg

            add_common_separator "Currently mounting bigstorage to nodes and installing packages until ubuntu repo is setup."
            pushd /mnt/bigstorage/releases/ceph/ceph/ubuntu-20.04/quincy/last_successful
                echo "Moving cephadm & ceph-mgr-cephadm to /var/tmp as they conflict with installation and are not required for deployment."
                ls | grep cephadm | xargs -I {} mv {} /var/tmp
                dpkg -i *.deb; apt-get -f install -y; apt --fix-broken install
            popd
            add_common_separator "Unmounting bigstorage"
            umount cortx-storage.colo.seagate.com:/mnt/data1/releases/ceph
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
    add_primary_separator "\t\tCeph Cluster Status"
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
    add_secondary_separator "Setup Ceph Manager"
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
    # Not required for RADOS GW.
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

function prereq_ceph_docker() {
    add_secondary_separator "Verify/Install Docker"
    if ! which docker; then
        if [[ "$ID" == "rocky"  ]]; then
            dnf config-manager --add-repo=https://download.docker.com/linux/centos/docker-ce.repo
            dnf install -y docker-ce docker-ce-cli containerd.io
            check_status "$HOSTNAME: Docker installation failed"
            systemctl enable docker && systemctl start docker

            add_common_separator "Enable local docker harbor registry"
            wget https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 && mv jq-linux64 jq && chmod +x jq && mv jq /usr/local/bin/ 
            mkdir -p /etc/docker/
            jq -n '{"insecure-registries": $ARGS.positional}' --args "cortx-docker.colo.seagate.com" > /etc/docker/daemon.json
            echo "Configured /etc/docker/daemon.json for local Harbor docker registry"

            systemctl restart docker && systemctl daemon-reload && systemctl enable docker
            echo "Docker Runtime Configured Successfully"

        fi
        
        if [[ "$ID" == "ubuntu" || "$ID" == "centos" ]]; then
            pushd $SCRIPTS_LOCATION
                curl -fsSL https://get.docker.com -o get-docker.sh
                chmod +x get-docker.sh
                ./get-docker.sh
                check_status "$HOSTNAME: Docker installation failed"

                add_common_separator "Enable local docker harbor registry"
                wget https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64 && mv jq-linux64 jq && chmod +x jq && mv jq /usr/local/bin/
                mkdir -p /etc/docker/
                jq -n '{"insecure-registries": $ARGS.positional}' --args "cortx-docker.colo.seagate.com" > /etc/docker/daemon.json
                echo "Configured /etc/docker/daemon.json for local Harbor docker registry"

                systemctl start docker && systemctl daemon-reload && systemctl enable docker
                echo "Docker Runtime Configured Successfully"
            popd
        fi
    fi

    add_secondary_separator "Install Cephadm"
    pushd $SCRIPTS_LOCATION
        curl --silent --remote-name --location https://github.com/ceph/ceph/raw/quincy/src/cephadm/cephadm && chmod +x cephadm
        unlink /usr/local/bin/cephadm 
        ln -s $SCRIPTS_LOCATION/cephadm /usr/local/bin/cephadm
    popd

    add_secondary_separator "Pull Ceph Docker image"
    docker pull "$CEPH_IMAGE"
}

function deploy_ceph_docker() {
    add_secondary_separator "Deploy Ceph"
    cephadm --image "$CEPH_IMAGE" --verbose bootstrap --mon-ip $(hostname -i) --initial-dashboard-user admin --initial-dashboard-password cephadmin --dashboard-password-noupdate --single-host-defaults --skip-pull --skip-monitoring-stack --allow-fqdn-hostname --allow-overwrite
}

function io_operation() {
    if [[ $CEPH_DOCKER_DEPLOYMENT = "true" ]]; then
        add_secondary_separator "Add RADOS-GW User"
        cephadm shell -- radosgw-admin user create --uid=io-test --display-name="io-ops"

    elif [[ $CEPH_DOCKER_DEPLOYMENT = "false" ]]; then
        add_secondary_separator "Add RADOS-GW User"
        radosgw-admin user create --uid=io-test --display-name="io-ops"

        add_secondary_separator "Setup Dashboard RADOS User"
        ceph dashboard set-rgw-credentials

    else
        echo "Not ceph deployment."
    fi

    pushd /var/tmp/
        ./io-sanity.sh
        check_status
    popd

    if [[ $CEPH_DOCKER_DEPLOYMENT = "true" ]]; then
        add_secondary_separator "Remove RADOS-GW User"
        cephadm shell -- radosgw-admin user rm --uid=io-test

    elif [[ $CEPH_DOCKER_DEPLOYMENT = "false" ]]; then
        add_secondary_separator "Remove RADOS-GW User"
        radosgw-admin user rm --uid=io-test

    else
        echo "Not ceph deployment."
    fi
}

function umount_osd() {
    echo "OSD mounts to unmount: $osd_mount"
    for mount in $osd_mount;	do
        umount $mount
    done
}

function destroy_cluster_vm() {
if ! which ceph; then
    add_secondary_separator "Ceph is not installed"
else
    add_secondary_separator "Stop all ceph daemon"
    systemctl stop ceph.target

    fsid=$(cat /etc/ceph/ceph.conf | grep fsid | awk '{ print $3 }')
    echo "$fsid"

    add_secondary_separator "Remove cluster"
    cephadm rm-cluster --fsid $fsid --force

    add_secondary_separator "Zap OSDs"
    cephadm zap-osds --fsid $fsid --force

    add_secondary_separator "Uninstall Ceph Packages"
    dnf repository-packages Ceph remove -y

    add_secondary_separator "Unmount osd tmpfs"
    osd_mount=$(df -hT | grep osd | awk '{ print $7}')

    # umounting 3 times as sometimes osd requires multiple umount even after zapping (possible bug or process improvement required for removing osds cleanup)
    umount_osd
    sleep 3
    umount_osd
    umount_osd

    add_secondary_separator "Remove files"
    files_to_remove=(
        "/etc/ceph"
        "/tmp/etc/ceph"
        "/tmp/monmap"
        "/etc/yum.repos.d/ceph.repo"
        "/var/lib/ceph"
        "/lib/systemd/system/ceph*"
        "/etc/yum.repos.d/_copr\:copr.fedorainfracloud.org\:tchaikov\:python*"
    )
    for file in ${files_to_remove[@]}; do
        if [ -f "$file" ] || [ -d "$file" ]; then
            echo "Removing file/folder $file"
            rm -rf $file
        fi
    done
fi
}

function destroy_cluster_docker() {
if ! which cephadm; then
    add_secondary_separator "Ceph is not deployed"
else
    add_secondary_separator "Stop all ceph daemon"
    systemctl stop ceph.target

    fsid=$(cat /etc/ceph/ceph.conf | grep fsid | awk '{ print $3 }')
    echo "$fsid"

    add_secondary_separator "Remove cluster"
    cephadm rm-cluster --fsid $fsid --force

    add_secondary_separator "Restore OSD disks"
    osd_disks=$(lvs -o +devices | grep ceph | awk '{ print $5 }')

    pv_volumes=()
    for device in $osd_disks; do
        block_device="${device%(*}"
        echo $block_device
        pv_volumes+=($block_device)
    done

    lvremove /dev/ceph-* -y
    vgdisplay | grep ceph | awk '{ print $3 }' | xargs -I {} vgremove {}

    for disk in ${!pv_volumes[@]}; do
        pvremove ${pv_volumes[$disk]}
    done

    add_secondary_separator "Remove docker images"
    docker system prune -a -f --filter "label!=vendor=Project Calico"

    add_secondary_separator "Remove files"
    files_to_remove=(
        "/etc/ceph"
        "/tmp/etc/ceph"
        "/tmp/monmap"
        "/etc/yum.repos.d/ceph.repo"
        "/var/lib/ceph"
        "/lib/systemd/system/ceph*"
        "/etc/yum.repos.d/_copr\:copr.fedorainfracloud.org\:tchaikov\:python*"
        "/usr/local/bin/cephadm"
    )
    for file in ${files_to_remove[@]}; do
        if [ -f "$file" ] || [ -d "$file" ]; then
            echo "Removing file/folder $file"
            rm -rf $file
        fi
    done
fi
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
    --prereq-ceph-docker)
        prereq_ceph_docker
    ;;
    --deploy-ceph-docker)
        deploy_ceph_docker
    ;;
    --io-operation)
        io_operation
    ;;
    --status)
        ceph_status
    ;;
    --destroy-cluster-vm)
        destroy_cluster_vm
    ;;
    --destroy-cluster-docker)
        destroy_cluster_docker
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;    
esac