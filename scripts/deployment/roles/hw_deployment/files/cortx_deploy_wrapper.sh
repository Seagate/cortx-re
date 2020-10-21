#! /bin/bash

BUILD_URL="$1"
NODE1="$2"
NODE2="$3"
CLUSTER_PASS="$4"

yum install -y python3
python3 -m venv venv_cortx
source venv_cortx/bin/activate
pip3 install -U git+https://github.com/Seagate/cortx-prvsnr@cortx-1.0#subdirectory=api/python
provisioner --version

if [[ "${BUILD_URL}" == *".iso" ]];then
    wget -q ${BUILD_URL}
    BUILD_URL=$(basename "${BUILD_URL}")
    echo "BUILD_URL : ${BUILD_URL}"  
fi

./cortx_deploy.sh "${NODE1}" "${NODE2}" "${CLUSTER_PASS}" "${BUILD_URL}"