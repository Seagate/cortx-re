pipeline {
    agent {
        node {
            label 'community-build-executor-ssc-vm-g3-rhev4-3187'
        }
    }
    
    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }


    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Cluster scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for CORTX Cluster scripts', trim: true)
        string(name: 'OS_VERSION', defaultValue: 'CentOS 7.9.2009 x86_64', description: 'Operating system version', trim: true)
        string(name: 'REGION', defaultValue: 'ap-south-1', description: 'AWS region', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VM details to be used. First node will be used as Primary node', name: 'hosts')
        password(name: 'SECRET_KEY', description: 'secret key for AWS account')
        password(name: 'ACCESS_KEY', description: 'access key for AWS account')
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

       stage ('clone repo') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'clone repo', script: '''
                    VM_IP=$(curl ipinfo.io/ip)
                    export OS_VERSION=${OS_VERSION}
                    export REGION=${REGION}
                    export SECRET_KEY=${SECRET_KEY}
                    export ACCESS_KEY=${ACCESS_KEY}
                    pushd solutions/community-deploy/cloud/AWS
                        ./tool_setup.sh
                        sed -i 's,os_version          =.*,os_version          = "'"$OS_VERSION"'",g' user.tfvars && sed -i 's,region              =.*,region              = "'"$REGION"'",g' user.tfvars && sed -i 's,security_group_cidr =.*,security_group_cidr = "'"$VM_IP/32"'",g' user.tfvars
                        cat user.tfvars | tail -3
                    popd
            '''
            }
        }            
        stage ('create EC2 instace') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Setting up EC2 instance', script: '''
                    pushd solutions/community-deploy/cloud/AWS
                        terraform validate && terraform apply -var-file user.tfvars --auto-approve
                    popd
            '''
            }
        }
        stage ('Network and Storage Configuration') {
            steps {
                script { build_stage = env.STAGE_NAME }
                catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                sh label: 'Setting up Network and Storage devices for CORTX. Script will reboot the instance on completion', script: '''
                    pushd solutions/community-deploy/cloud/AWS
                        AWS_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.cortx_deploy_ip_addr.value 2>&1 | tee ip.txt)
                        IP=$(cat ip.txt | tr -d '""')                        
                        ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@${IP} sudo bash /home/centos/setup.sh
                        sleep 120
                    popd
            '''
            }
        }
    }
        stage ('execute cortx build script') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'executing cortx build script', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    export CORTX_RE_BRANCH=${CORTX_RE_BRANCH}
                    AWS_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.cortx_deploy_ip_addr.value 2>&1 | tee ip.txt)
                    IP=$(cat ip.txt | tr -d '""')                
                    ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@${IP} "export CORTX_RE_BRANCH=$CORTX_RE_BRANCH; git clone https://github.com/Seagate/cortx-re; pushd /home/centos/cortx-re/solutions/community-deploy; time sudo ./build-cortx.sh -b ${CORTX_RE_BRANCH}"
                popd
            '''
            }
        }
        stage ('destroy AWS infrastructure') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'executing cortx build script', script: '''
                pushd solutions/community-deploy/cloud/AWS
                    terraform validate && terraform destroy -var-file user.tfvars --auto-approve
                popd
            '''
            }
        }
    }
}