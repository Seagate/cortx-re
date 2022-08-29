pipeline {
    agent {
        node {
            //label 'mukul-community-build-multi-node'
            label 'communitybuild-multinode-ssc-vm'
        }
    }
    options {
        timeout(time: 360, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'mukul-multinode-automation', description: 'Branch or GitHash for CORTX Cluster scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for CORTX Cluster scripts', trim: true)
        string(name: 'OS_VERSION', defaultValue: 'CentOS 7.9.2009 x86_64', description: 'Operating system version', trim: true)
        string(name: 'REGION', defaultValue: 'ap-south-1', description: 'AWS region', trim: true)
        string(name: 'KEY_NAME', defaultValue: 'devops-key', description: 'Key name', trim: true)
        string(name: 'COMMUNITY_USE', defaultValue: 'yes', description: 'Only use during community deployment', trim: true)
        string(name: 'VOLUME_COUNT', defaultValue: '9', description: 'EBS volume', trim: true)
        string(name: 'VOLUME_SIZE', defaultValue: '10', description: 'EBS volume size', trim: true)
        string(name: 'INSTANCE_COUNT', defaultValue: '4', description: 'Instance count', trim: true)
        string(name: 'INSTANCE_TAG_NAME', defaultValue: 'cortx-multinode', description: 'Tag name', trim: true)
        password(name: 'SECRET_KEY', description: 'secret key for AWS account')
        password(name: 'ACCESS_KEY', description: 'access key for AWS account')
        password(name: 'ROOT_PASSWORD', description: 'Root password for EC2 instances')
    }
        stages {
          stage('Execute cortx build script') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Executing cortx build image script on Primary node', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    export CORTX_RE_BRANCH=${CORTX_RE_BRANCH}
                    export PRIMARY_PUBLIC_IP=$(cat ip_public.txt | jq '.[0]'| tr -d '",[]')
                    sleep 120
                    ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@${PRIMARY_PUBLIC_IP} "export CORTX_RE_BRANCH=$CORTX_RE_BRANCH; git clone $CORTX_RE_REPO; pushd /home/centos/cortx-re/solutions/community-deploy; time sudo ./build-cortx.sh"
                    popd
            '''
            }
        }
        stage('Setup K8s cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Setting up K8s cluster on EC2', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    export WORKER_IP=$(cat ip_public.txt | jq '.[1]','.[2]' | tr -d '",[]')
                    export CORTX_SERVER_IMAGE=$HOST1:8080/seagate/cortx-rgw:2.0.0-0
                    export CORTX_DATA_IMAGE=$HOST1:8080/seagate/cortx-data:2.0.0-0
                    export CORTX_CONTROL_IMAGE=$HOST1:8080/seagate/cortx-control:2.0.0-0
                    ssh -i cortx.pem -o StrictHostKeyChecking=no centos@${PRIMARY_PUBLIC_IP} "sudo -- sh -c 'pushd /home/centos/cortx-re/solutions/kubernetes && ./cluster-setup.sh true && sed -i 's,cortx-docker.colo.seagate.com,${HOST1}:8080,g' /etc/docker/daemon.json && systemctl restart docker && sleep 240'"
                    for wp in $WORKER_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"${wp}" "sudo -- sh -c 'sed -i 's,cortx-docker.colo.seagate.com,${HOST1}:8080,g' /etc/docker/daemon.json && systemctl restart docker && sleep 240 && docker pull ${CORTX_SERVER_IMAGE} && docker pull ${CORTX_DATA_IMAGE} && docker pull ${CORTX_CONTROL_IMAGE}'";done
                    popd
            '''
            }
        }
    }
