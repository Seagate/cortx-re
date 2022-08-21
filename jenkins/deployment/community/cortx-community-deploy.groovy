pipeline {
    agent {
        node {
            label 'mukul-community-build-multi-node'
        }
    }

    //triggers { cron('0 22 * * 1,3,5') }
    options {
        timeout(time: 360, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '1'))
        ansiColor('xterm')
    }

    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Cluster scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/mukul-seagate11/cortx-re-1', description: 'Repository for CORTX Cluster scripts', trim: true)
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
        password(name: 'ROOT_PASSWORD', description: 'Root password for EC2 instance')

    // Please configure ROOT_PASSWORD parameter in Jenkins job configuration.
    }

        stages {
            stage('Checkout Script') {
                steps {
                    cleanWs()
                    script {
                        checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])
                    }
                }
            }

        stage('Install Prerequisite tools') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'install tools', script: '''
                VM_IP=$(curl ipinfo.io/ip)
                export OS_VERSION=${OS_VERSION}
                export REGION=${REGION}
                export SECRET_KEY=${SECRET_KEY}
                export ACCESS_KEY=${ACCESS_KEY}
                export KEY_NAME=${KEY_NAME}
                export VOLUME_COUNT=${VOLUME_COUNT}
                export VOLUME_SIZE=${VOLUME_SIZE}
                export INSTANCE_COUNT=${INSTANCE_COUNT}
                export INSTANCE_TAG_NAME=${INSTANCE_TAG_NAME}
                            rm -rvf /usr/local/bin/aws /usr/local/bin/aws_completer /usr/local/aws-cli >/dev/null 2>&1
                            curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && yum install unzip -y && unzip awscliv2.zip
                            ./aws/install
                            aws configure set default.region $REGION; aws configure set aws_access_key_id $ACCESS_KEY; aws configure set aws_secret_access_key $SECRET_KEY
                        pushd solutions/community-deploy/cloud/AWS
                            ./tool_setup.sh
                            sed -i 's,os_version          =.*,os_version          = "'"$OS_VERSION"'",g' user.tfvars && sed -i 's,region              =.*,region              = "'"$REGION"'",g' user.tfvars && sed -i 's,security_group_cidr =.*,security_group_cidr = "'"$VM_IP/32"'",g' user.tfvars && sed -i 's,instance_count          =.*,instance_count          = "'"$INSTANCE_COUNT"'",g' user.tfvars && sed -i 's,ebs_volume_count          =.*,ebs_volume_count          = "'"$VOLUME_COUNT"'",g' user.tfvars && sed -i 's,ebs_volume_size          =.*,ebs_volume_size          = "'"$VOLUME_SIZE"'",g' user.tfvars && sed -i 's,tag_name          =.*,tag_name          = "'"$INSTANCE_TAG_NAME"'",g' user.tfvars
                            echo key_name            = '"'$KEY_NAME'"' | cat >>user.tfvars
                            cat user.tfvars | tail -4
                            popd
                '''
            }
        }
        stage('Create Multi EC2 instances') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Setting up multi EC2 instances', script: '''
                    pushd solutions/community-deploy/cloud/AWS
                        terraform validate && terraform apply -var-file user.tfvars --auto-approve
                        popd
            '''
            }
        }
        stage('Configure network and storage') {
            steps {
                script { build_stage = env.STAGE_NAME }
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    sh label: 'Setting up Network and Storage devices for CORTX. Script will reboot the instance on completion', script: '''
                    pushd solutions/community-deploy/cloud/AWS
                        PUBLIC_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_public_ip_addr.value 2>&1 | tee ip_public.txt | tr -d '",[]' | sed '/^$/d')
                        for ip in $PUBLIC_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@${ip} sudo bash /home/centos/setup.sh && sleep 240;done
                        popd
            '''
                }
            }
        }

        stage('EC2 connection prerequisites') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Changing root password & creating hosts file', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    export ROOT_PASSWORD=${ROOT_PASSWORD}
                    PUBLIC_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_public_ip_addr.value 2>&1 | tee ip_public.txt | tr -d '",[]' | sed '/^$/d')
                    terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_private_dns.value 2>&1 | tee ec2_hostname.txt
                    export HOST1=$(cat ec2_hostname.txt | jq '.[0]'| tr -d '",[]')
                    export HOST2=$(cat ec2_hostname.txt | jq '.[1]'| tr -d '",[]')
                    export HOST3=$(cat ec2_hostname.txt | jq '.[2]'| tr -d '",[]')
                    export HOST4=$(cat ec2_hostname.txt | jq '.[3]'| tr -d '",[]')
                    for ip in $PUBLIC_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@${ip} "export ROOT_PASSWORD=$ROOT_PASSWORD && echo $ROOT_PASSWORD | sudo passwd --stdin root && git clone https://github.com/mukul-seagate11/cortx-re-1 && pushd /home/centos/cortx-re-1/solutions/kubernetes && touch hosts && echo hostname=${HOST1},user=root,pass= > hosts && sed -i 's,pass=.*,pass=$ROOT_PASSWORD,g' hosts && echo hostname=${HOST2},user=root,pass= >> hosts && sed -i 's,pass=.*,pass=$ROOT_PASSWORD,g' hosts && echo hostname=${HOST3},user=root,pass= >> hosts && sed -i 's,pass=.*,pass=$ROOT_PASSWORD,g' hosts && echo hostname=${HOST4},user=root,pass= >> hosts && sed -i 's,pass=.*,pass=$ROOT_PASSWORD,g' hosts && cat hosts && sleep 60";done
                    popd
            '''
            }
        }

        stage('Execute cortx build script') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Executing cortx build image script on Primary node..........', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    export CORTX_RE_BRANCH=${CORTX_RE_BRANCH}
                    PRIMARY_PUBLIC_IP=$(cat ip_public.txt | jq '.[0]'| tr -d '",[]')
                    ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@${PRIMARY_PUBLIC_IP} "export CORTX_RE_BRANCH=$CORTX_RE_BRANCH; pushd /home/centos/cortx-re-1/solutions/community-deploy; time sudo ./build-cortx.sh -b ${CORTX_RE_BRANCH}"
                    popd
            '''
            }
        }

        stage('Setup K8s cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Setting up K8s cluster on EC2', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    PRIMARY_PUBLIC_IP=$(cat ip_public.txt | jq '.[0]'| tr -d '",[]')
                    WORKER_IP=$(cat ip_public.txt | jq '.[1]','.[2]' | tr -d '",[]')
                    export HOST1=$(cat ec2_hostname.txt | jq '.[0]'| tr -d '",[]')
                    export CORTX_SERVER_IMAGE="cortx-rgw:2.0.0-0"
                    export CORTX_DATA_IMAGE="cortx-data:2.0.0-0"
                    export CORTX_CONTROL_IMAGE="cortx-control:2.0.0-0"
                    export CORTX_ALL_IMAGE="cortx-all:2.0.0-0"
                    ssh -i cortx.pem -o StrictHostKeyChecking=no centos@${PRIMARY_PUBLIC_IP} sudo -- sh -c "pushd /home/centos/cortx-re-1/solutions/kubernetes && ./cluster-setup.sh true && sed -i 's,cortx-docker.colo.seagate.com,${HOST1},g' /etc/docker/daemon.json && systemctl restart docker && sleep 5"
                    for wp in $WORKER_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@${wp} sudo -- sh -c "sed -i 's,cortx-docker.colo.seagate.com,${HOST1}:8080,g' /etc/docker/daemon.json && systemctl restart docker && sleep 5 && docker pull ${HOST1}:8080/seagate/${CORTX_SERVER_IMAGE} && docker pull ${HOST1}:8080/seagate/${CORTX_DATA_IMAGE} && docker pull ${HOST1}:8080/seagate/${CORTX_CONTROL_IMAGE} && docker pull ${HOST1}:8080/seagate/${CORTX_ALL_IMAGE}";done
                    popd
            '''
            }
        }

        stage('Deploy multi-node cortx cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: '....... Deploying multi-node cortx cluster and pull locally generated cortx images on worker nodes ......', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    export SOLUTION_CONFIG_TYPE="automated"
                    export CORTX_SERVER_IMAGE="cortx-rgw:2.0.0-0"
                    export CORTX_DATA_IMAGE="cortx-data:2.0.0-0"
                    export CORTX_CONTROL_IMAGE="cortx-control:2.0.0-0"
                    export COMMUNITY_USE=${COMMUNITY_USE}
                    PRIMARY_PUBLIC_IP=$(cat ip_public.txt | jq '.[0]'| tr -d '",[]')
                    ssh -i cortx.pem -o StrictHostKeyChecking=no centos@${PRIMARY_PUBLIC_IP} "pushd /home/centos/cortx-re-1/solutions/kubernetes && export SOLUTION_CONFIG_TYPE=${SOLUTION_CONFIG_TYPE} && export COMMUNITY_USE=${COMMUNITY_USE} && export CORTX_SERVER_IMAGE=${CORTX_SERVER_IMAGE} && export CORTX_DATA_IMAGE=${CORTX_DATA_IMAGE} && export CORTX_CONTROL_IMAGE=${CORTX_CONTROL_IMAGE} && sudo env SOLUTION_CONFIG_TYPE=${SOLUTION_CONFIG_TYPE} env CORTX_SERVER_IMAGE=${CORTX_SERVER_IMAGE} env CORTX_CONTROL_IMAGE=${CORTX_CONTROL_IMAGE} env CORTX_DATA_IMAGE=${CORTX_DATA_IMAGE} env COMMUNITY_USE=${COMMUNITY_USE} ./cortx-deploy.sh --cortx-cluster"
                    popd
            '''
            }
        }

        stage('IO Sanity') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'IO Sanity on CORTX Cluster to validate bucket creation and object upload in deployed cluster', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    PRIMARY_PUBLIC_IP=$(cat ip_public.txt | jq '.[0]'| tr -d '",[]')
                    ssh -i cortx.pem -o StrictHostKeyChecking=no centos@${PRIMARY_PUBLIC_IP} "pushd /home/centos/cortx-re-1/solutions/kubernetes && sudo ./cortx-deploy.sh --io-sanity"
                    popd
            '''
            }
        }

        stage('Clean up') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Destroying EC2 instances........', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    terraform destroy -var-file user.tfvars --auto-approve
                    popd
        '''
            }
        }
    }
}
