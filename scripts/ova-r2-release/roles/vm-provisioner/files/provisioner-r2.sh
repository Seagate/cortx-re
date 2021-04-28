#!/bin/bash

<<<<<<< HEAD
<<<<<<< HEAD
CORTX_RELEASE_REPO="$1"
yum install -y yum-utils
yum-config-manager --add-repo "${CORTX_RELEASE_REPO}3rd_party/"
yum install --nogpgcheck -y python3 python36-m2crypto salt salt-master salt-minion
rm -rf /etc/yum.repos.d/*3rd_party*.repo
yum-config-manager --add-repo "${CORTX_RELEASE_REPO}cortx_iso/"
=======
#yum install -y python3
#python3 -m venv venv_cortx
#source venv_cortx/bin/activate
#pip3 install -U git+https://github.com/Seagate/cortx-prvsnr@cortx-1.0#subdirectory=api/python
#provisioner --version

yum install -y yum-utils
yum-config-manager --add-repo "${BUILD_URL}/3rd_party/"
yum install --nogpgcheck -y python3 python36-m2crypto salt salt-master salt-minion
rm -rf /etc/yum.repos.d/*3rd_party*.repo
yum-config-manager --add-repo "${BUILD_URL}/cortx_iso/"
>>>>>>> feat: add R2 provisioner steps
yum install --nogpgcheck -y python36-cortx-prvsnr
rm -rf /etc/yum.repos.d/*cortx_iso*.repo
yum clean all
rm -rf /var/cache/yum/
=======
yum install -y python3
python3 -m venv venv_cortx
source venv_cortx/bin/activate
pip3 install -U git+https://github.com/Seagate/cortx-prvsnr@main#subdirectory=api/python
provisioner --version

#yum install -y yum-utils
#yum-config-manager --add-repo "${BUILD_URL}/3rd_party/"
#yum install --nogpgcheck -y python3 python36-m2crypto salt salt-master salt-minion
#rm -rf /etc/yum.repos.d/*3rd_party*.repo
#yum-config-manager --add-repo "${BUILD_URL}/cortx_iso/"
#yum install --nogpgcheck -y python36-cortx-prvsnr
#rm -rf /etc/yum.repos.d/*cortx_iso*.repo
#yum clean all
#rm -rf /var/cache/yum/
>>>>>>> chore: move provisioner setup to dev from prod

./script
