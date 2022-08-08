pipeline {
    agent {
        node {
            label 'community-build-executor'
        }
    }
    triggers { cron('0 22 * * 1,3,5') }
    options {
        timeout(time: 360, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }


    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Cluster scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for CORTX Cluster scripts', trim: true)
        string(name: 'CORTX_TAG', defaultValue: 'main', description: 'Branch or GitHash for generaing CORTX container images', trim: true)
        string(name: 'OS_VERSION', defaultValue: 'CentOS 7.9.2009 x86_64', description: 'Operating system version', trim: true)
        string(name: 'REGION', defaultValue: 'ap-south-1', description: 'AWS region', trim: true)
        string(name: 'KEY_NAME', defaultValue: 'automation-key', description: 'Key name', trim: true)
        string(name: 'COMMUNITY_USE', defaultValue: 'yes', description: 'Only use during community deployment', trim: true)
        password(name: 'SECRET_KEY', description: 'secret key for AWS account')
        password(name: 'ACCESS_KEY', description: 'access key for AWS account')
        
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

        stage ('Install tools') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'install tools', script: '''
                VM_IP=$(curl ipinfo.io/ip)
                export OS_VERSION=${OS_VERSION}
                export REGION=${REGION}
                export SECRET_KEY=${SECRET_KEY}
                export ACCESS_KEY=${ACCESS_KEY}
                export KEY_NAME=${KEY_NAME}
                rm -rvf /usr/local/bin/aws /usr/local/bin/aws_completer /usr/local/aws-cli >/dev/null 2>&1
                curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && yum install unzip -y && unzip awscliv2.zip
                ./aws/install
                aws configure set default.region $REGION; aws configure set aws_access_key_id $ACCESS_KEY; aws configure set aws_secret_access_key $SECRET_KEY
                pushd solutions/community-deploy/cloud/AWS
                    ./tool_setup.sh
                    sed -i 's,os_version          =.*,os_version          = "'"$OS_VERSION"'",g' user.tfvars && sed -i 's,region              =.*,region              = "'"$REGION"'",g' user.tfvars && sed -i 's,security_group_cidr =.*,security_group_cidr = "'"$VM_IP/32"'",g' user.tfvars
                    echo key_name            = '"'$KEY_NAME'"' | cat >>user.tfvars
                    cat user.tfvars | tail -4
                popd
                '''
            }
        }

        stage ('Create EC2 instace') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Setting up EC2 instance', script: '''
                    pushd solutions/community-deploy/cloud/AWS
                        terraform validate && terraform apply -var-file user.tfvars --auto-approve
                    popd
            '''
            }
        }

        stage ('Network and storage configuration') {
            steps {
                script { build_stage = env.STAGE_NAME }
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                sh label: 'Setting up Network and Storage devices for CORTX. Script will reboot the instance on completion', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    AWS_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.cortx_deploy_ip_addr.value 2>&1 | tee ip.txt)
                    IP=$(cat ip.txt | tr -d '""')                        
                    ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@${IP} sudo bash /home/centos/setup.sh
                    sleep 240
                popd
                '''
                }
            }
        }

        stage ('Execute cortx build script') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'executing cortx build image script', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    export CORTX_RE_BRANCH=${CORTX_RE_BRANCH}
                    export CORTX_TAG=${CORTX_TAG}
                    AWS_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.cortx_deploy_ip_addr.value 2>&1 | tee ip.txt)
                    IP=$(cat ip.txt | tr -d '""')                
                    ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@${IP} "export CORTX_RE_BRANCH=$CORTX_RE_BRANCH; git clone $CORTX_RE_REPO -b $CORTX_RE_BRANCH; pushd /home/centos/cortx-re/solutions/community-deploy; time sudo ./build-cortx.sh -b ${CORTX_TAG}"
                popd
            '''
            }
        }

        stage ('EC2 connection prerequisites') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Changing root password and creating hosts file', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    export ROOT_PASSWORD=${ROOT_PASSWORD}
                    AWS_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.cortx_deploy_ip_addr.value 2>&1 | tee ip.txt)
                    IP=$(cat ip.txt | tr -d '""')                
                    ssh -i cortx.pem -o StrictHostKeyChecking=no centos@${IP} "export ROOT_PASSWORD=$ROOT_PASSWORD && echo $ROOT_PASSWORD | sudo passwd --stdin root && pushd /home/centos/cortx-re/solutions/kubernetes && echo "'"hostname=$HOSTNAME,user=root,pass="'" > hosts && sed -i 's,pass=.*,pass=$ROOT_PASSWORD,g' hosts && cat hosts"
                    popd
            '''
            }
        }

        stage ('Setup K8s cluster on EC2') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'setting up K8s cluster on EC2', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    AWS_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.cortx_deploy_ip_addr.value 2>&1 | tee ip.txt)
                    IP=$(cat ip.txt | tr -d '""')                
                    ssh -i cortx.pem -o StrictHostKeyChecking=no centos@${IP} "pushd /home/centos/cortx-re/solutions/kubernetes && sudo ./cluster-setup.sh true"
                    popd
                '''
            }
        }

        stage ('Deploy 1N cortx cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploying 1N cortx cluster on EC2', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    AWS_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.cortx_deploy_ip_addr.value 2>&1 | tee ip.txt)
                    IP=$(cat ip.txt | tr -d '""')
                    ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@${IP} 'sudo sed -i 's/cortx-docker.colo.seagate.com/'$HOSTNAME':8080/g' /etc/docker/daemon.json && sudo systemctl restart docker' 
                    ssh -i cortx.pem -o StrictHostKeyChecking=no centos@${IP} 'pushd /home/centos/cortx-re/solutions/kubernetes && 
                    export SOLUTION_CONFIG_TYPE='automated' && 
                    export COMMUNITY_USE='yes' && 
                    export CORTX_SERVER_IMAGE=$HOSTNAME:8080/seagate/cortx-rgw:2.0.0-0 && 
                    export CORTX_DATA_IMAGE=$HOSTNAME:8080/seagate/cortx-data:2.0.0-0 && 
                    export CORTX_CONTROL_IMAGE=$HOSTNAME:8080/seagate/cortx-control:2.0.0-0 && 
                    sudo env SOLUTION_CONFIG_TYPE=${SOLUTION_CONFIG_TYPE} env CORTX_SERVER_IMAGE=${CORTX_SERVER_IMAGE} env CORTX_CONTROL_IMAGE=${CORTX_CONTROL_IMAGE} env CORTX_DATA_IMAGE=${CORTX_DATA_IMAGE} env COMMUNITY_USE=${COMMUNITY_USE} ./cortx-deploy.sh --cortx-cluster'
                    popd
            '''
            }
        }

        stage ('IO Sanity') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'IO Sanity on CORTX Cluster to validate bucket creation and object upload in deployed cluster', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    AWS_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.cortx_deploy_ip_addr.value 2>&1 | tee ip.txt)
                    IP=$(cat ip.txt | tr -d '""')
                    ssh -i cortx.pem -o StrictHostKeyChecking=no centos@${IP} "pushd /home/centos/cortx-re/solutions/kubernetes && sudo ./cortx-deploy.sh --io-sanity"
                    popd
            '''
            }
        }
    }

    post {
        always {
            retry(count: 3) {
            script { build_stage = env.STAGE_NAME }
                sh label: 'destroying EC2 instance', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    terraform validate && terraform destroy -var-file user.tfvars --auto-approve
                popd
                '''
            }
           script {

            // Jenkins Summary
            clusterStatus = ""
            if ( currentBuild.currentResult == "SUCCESS" ) {
                MESSAGE = "CORTX Community Deploy is Success for the build ${build_id}"
                ICON = "accept.gif"
                STATUS = "SUCCESS"
            } else if ( currentBuild.currentResult == "FAILURE" ) {
                manager.buildFailure()
                MESSAGE = "CORTX Community Deploy is Failed for the build ${build_id}"
                ICON = "error.gif"
                STATUS = "FAILURE"

            } else {
                manager.buildUnstable()
                MESSAGE = "CORTX Community Deploy Setup is Unstable"
                ICON = "warning.gif"
                STATUS = "UNSTABLE"
            }
            
            clusterStatusHTML = "<pre>${clusterStatus}</pre>"

            manager.createSummary("${ICON}").appendText("<h3>CORTX Community Deploy ${currentBuild.currentResult} </h3><p>Please check <a href=\"${BUILD_URL}/console\">cluster setup logs</a> for more info <h4>Cluster Status:</h4>${clusterStatusHTML}", false, false, false, "red")

            // Email Notification
            env.build_stage = "${build_stage}"
            env.cluster_status = "${clusterStatusHTML}"

            def toEmail = ""
            def recipientProvidersClass = [[$class: 'DevelopersRecipientProvider']]
            if ( manager.build.result.toString() == "FAILURE" ) {
                toEmail = "CORTX.DevOps.RE@seagate.com"
                recipientProvidersClass = [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
            }
            emailext ( 
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