pipeline {
    
    agent {
        node {
            label 'community-build-executor'
        }
    }

    triggers { cron('TZ=Asia/Calcutta\n20 12 * * 1') }
   
    options {
        timeout(time: 360, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Cluster scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for CORTX Cluster scripts', trim: true)
        string(name: 'CORTX_TAG', defaultValue: 'main', description: 'Branch or GitHash for generaing CORTX container images', trim: true)
        string(name: 'OS_VERSION', defaultValue: 'CentOS Linux 7 x86_64 - 2211', description: 'Operating system version', trim: true)
        string(name: 'REGION', defaultValue: 'ap-south-1', description: 'AWS region', trim: true)
        string(name: 'KEY_NAME', defaultValue: 'automation-key', description: 'Key name', trim: true)
        string(name: 'COMMUNITY_USE', defaultValue: 'yes', description: 'Only use during community deployment', trim: true)
        string(name: 'EBS_VOLUME_COUNT', defaultValue: '9', description: 'EBS volume count', trim: true)
        string(name: 'EBS_VOLUME_SIZE', defaultValue: '25', description: 'EBS volume size in GB', trim: true)
        string(name: 'INSTANCE_COUNT', defaultValue: '4', description: 'EC2 instance count', trim: true)
        string(name: 'AWS_INSTANCE_TAG_NAME', defaultValue: 'cortx-multinode', description: 'Tag name for EC2 instances', trim: true)
        // Please configure ROOT_PASSWORD, ACCESS_KEY and SECRET_KEY parameters in Jenkins job configuration.
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
                sh label: 'Install prerequisite tools', script: '''
                CIDR=$(curl -s ipinfo.io/ip | sed 's/$/\\/32/')
                export OS_VERSION=${OS_VERSION}
                export REGION=${REGION}
                export SECRET_KEY=${SECRET_KEY}
                export ACCESS_KEY=${ACCESS_KEY}
                export KEY_NAME=${KEY_NAME}
                export EBS_VOLUME_COUNT=${EBS_VOLUME_COUNT}
                export EBS_VOLUME_SIZE=${EBS_VOLUME_SIZE}
                export INSTANCE_COUNT=${INSTANCE_COUNT}
                export AWS_INSTANCE_TAG_NAME=${AWS_INSTANCE_TAG_NAME}
                rm -rvf /usr/local/bin/aws /usr/local/bin/aws_completer /usr/local/aws-cli >/dev/null 2>&1
                pushd solutions/community-deploy/cloud/AWS
                    ./tool_setup.sh
                    sed -i \
                    -e "/os_version/s/<OS VERSION>/$OS_VERSION/g" \
                    -e "s|<YOUR PUBLIC IP CIDR>|$CIDR|g" \
                    -e "/region/s/<AWS REGION>/$REGION/g" \
                    -e "/ebs_volume_count/s/<NUMBER OF EBS VOLUMES>/$EBS_VOLUME_COUNT/g" \
                    -e "/ebs_volume_size/s/<EBS VOLUME SIZE>/$EBS_VOLUME_SIZE/g" \
                    -e "/instance_count/s/<NUMBER OF EC2 INSTANCES>/$INSTANCE_COUNT/g" \
                    -e "/tag_name/s/<YOUR TAG NAME FOR EC2 INSTANCES>/$AWS_INSTANCE_TAG_NAME/g" user.tfvars
                    cat user.tfvars
                popd
                '''
            }
        }
        stage('Create EC2 instances') {
            
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Setting up EC2 instances', script: '''
                    pushd solutions/community-deploy/cloud/AWS
                        terraform validate && terraform apply -var-file user.tfvars --auto-approve
                    popd
                '''
            }
        }
        stage('Configure network and storage') {

            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    env.PUBLIC_IP_LIST = sh( script: '''
                    cd solutions/community-deploy/cloud/AWS && terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_public_ip_addr.value | jq .[] | tr -d '"'
                    ''', returnStdout: true).trim()
                    env.PRIMARY_PUBLIC_IP = sh( script: '''
                    cd solutions/community-deploy/cloud/AWS && terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_public_ip_addr.value | jq .[0] | tr -d '"'
                    ''', returnStdout: true).trim()
                    env.PRIVATE_HOSTNAME_LIST = sh( script: '''
                    cd solutions/community-deploy/cloud/AWS && terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_private_dns.value | jq .[] | tr -d '"'
                    ''', returnStdout: true).trim()
                    env.PRIMARY_PRIVATE_HOSTNAME = sh( script: '''
                    cd solutions/community-deploy/cloud/AWS && terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_private_dns.value | jq .[0] | tr -d '"'
                    ''', returnStdout: true).trim()
                }
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    sh label: 'Setting up Network and Storage devices for CORTX. Script will reboot the instance on completion', script: '''
                    pushd solutions/community-deploy/cloud/AWS
                        export ROOT_PASSWORD=${ROOT_PASSWORD}
                        for ip in $PUBLIC_IP_LIST;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"${ip}" sudo bash /home/centos/setup.sh $ROOT_PASSWORD && sleep 300;done
                    popd
                    '''
                }
            }
        }

        stage('Execute cortx build script') {
            
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Executing cortx build image script on Primary node', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    sleep 240
                    echo "Generating CORTX packages and images on ${PRIMARY_PUBLIC_IP}"
                    ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"${PRIMARY_PUBLIC_IP}" "export CORTX_RE_BRANCH=$CORTX_RE_BRANCH; git clone $CORTX_RE_REPO -b $CORTX_RE_BRANCH; pushd /home/centos/cortx-re/solutions/community-deploy; time sudo ./build-cortx.sh -b ${CORTX_TAG}"
                    popd
                '''
            }
        }

        stage('EC2 connection prerequisites') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Changing root password & creating hosts file', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    export ROOT_PASSWORD=${ROOT_PASSWORD}
                    ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$PRIMARY_PUBLIC_IP "rm -f /home/centos/cortx-re/solutions/kubernetes/hosts"
                    for ip in $PRIVATE_HOSTNAME_LIST;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$PRIMARY_PUBLIC_IP "pushd /home/centos/cortx-re/solutions/kubernetes && echo hostname="${ip}",user=root,pass='${ROOT_PASSWORD}' >> hosts && cat hosts";done
                popd
            '''
            }
        }

        stage('Setup K8s cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Setting up K8s cluster on EC2', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    if [ ${INSTANCE_COUNT} == 1 ]; then
                        ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"${PRIMARY_PUBLIC_IP}" "pushd /home/centos/cortx-re/solutions/kubernetes && sudo ./cluster-setup.sh true"
                    else
                        ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"${PRIMARY_PUBLIC_IP}" "pushd /home/centos/cortx-re/solutions/kubernetes && sudo ./cluster-setup.sh"
                    fi
                popd
            '''
            }
        }

        stage('Configure Local Registry') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Generate locally build cortx images on worker nodes', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    for ip in $PUBLIC_IP_LIST;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"${ip}" "sudo -- sh -c 'sed -i 's,cortx-docker.colo.seagate.com,${PRIMARY_PRIVATE_HOSTNAME}:8080,g' /etc/docker/daemon.json && systemctl restart docker && sleep 120'";done
                popd
            '''
            }
        }

        stage('Deploy CORTX cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploying multi-node cortx cluster by locally generating images', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    export SOLUTION_CONFIG_TYPE='automated'
                    export COMMUNITY_USE='yes'
                    export CORTX_SERVER_IMAGE="${PRIMARY_PRIVATE_HOSTNAME}:8080/seagate/cortx-rgw:2.0.0-0"
                    export CORTX_DATA_IMAGE="${PRIMARY_PRIVATE_HOSTNAME}:8080/seagate/cortx-data:2.0.0-0"
                    export CORTX_CONTROL_IMAGE="${PRIMARY_PRIVATE_HOSTNAME}:8080/seagate/cortx-control:2.0.0-0"
                    ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"${PRIMARY_PUBLIC_IP}" 'pushd /home/centos/cortx-re/solutions/kubernetes &&
                    export CORTX_SERVER_IMAGE='${CORTX_SERVER_IMAGE}' &&
                    export CORTX_DATA_IMAGE='${CORTX_DATA_IMAGE}' &&
                    export CORTX_CONTROL_IMAGE='${CORTX_CONTROL_IMAGE}' &&
                    sudo env SOLUTION_CONFIG_TYPE='${SOLUTION_CONFIG_TYPE}' env CORTX_SERVER_IMAGE='${CORTX_SERVER_IMAGE}' env CORTX_CONTROL_IMAGE='${CORTX_CONTROL_IMAGE}' env CORTX_DATA_IMAGE='${CORTX_DATA_IMAGE}' env COMMUNITY_USE='${COMMUNITY_USE}' ./cortx-deploy.sh --cortx-cluster'
                popd
                '''
            }
        }

        stage('Basic I/O Test') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'IO Sanity on CORTX Cluster to validate bucket creation and object upload in deployed cluster', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"${PRIMARY_PUBLIC_IP}" 'pushd /home/centos/cortx-re/solutions/kubernetes && sudo ./cortx-deploy.sh --io-sanity'
                popd
            '''
            }
        }

        stage('Basic Management Path Check') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Basic Management Path Check', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"${PRIMARY_PUBLIC_IP}" 'pushd /home/centos/cortx-re/solutions/kubernetes && sudo ./cortx-deploy.sh --mangement-health-check'
                popd
                '''
            }
        }
    }

    post {
        always {
            retry(count: 3) {
                    sh label: 'Destroying EC2 instance', script: '''
                    pushd solutions/community-deploy/cloud/AWS
                        terraform validate && terraform destroy -var-file user.tfvars --auto-approve
                    popd
                    '''
            }

            script {
                // Jenkins Summary
                CommunityBuildStatus = ''
                if ( currentBuild.currentResult == 'SUCCESS' ) {
                    MESSAGE = "CORTX Community Deploy is Success for the build ${build_id}"
                    ICON = 'accept.gif'
                    STATUS = 'SUCCESS'
                } else if ( currentBuild.currentResult == 'FAILURE' ) {
                    manager.buildFailure()
                    MESSAGE = "CORTX Community Deploy is Failed for the build ${build_id}"
                    ICON = 'error.gif'
                    STATUS = 'FAILURE'
                } else {
                    manager.buildUnstable()
                    MESSAGE = 'CORTX Community Deploy Setup is Unstable'
                    ICON = 'warning.gif'
                    STATUS = 'UNSTABLE'
                }

                clusterStatusHTML  = "<pre>${CommunityBuildStatus}</pre>"

                // Email Notification
                env.build_stage = "${build_stage}"
                env.cluster_status = "${clusterStatusHTML}"

                def toEmail = ''
                def recipientProvidersClass = [[$class: 'DevelopersRecipientProvider']]
                if ( manager.build.result.toString() == 'FAILURE' ) {
                    toEmail = 'CORTX.DevOps.RE@seagate.com'
                    recipientProvidersClass = [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
                }
                emailext(
                body: '''${SCRIPT, template="cluster-setup-email.template"}''',
                mimeType: 'text/html',
                subject: "[Cortx Community Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                attachLog: true,
                to: toEmail,
                recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}
