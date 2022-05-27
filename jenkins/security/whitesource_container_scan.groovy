pipeline {
    agent {
        node {
            label 'docker-k8-deployment-node'
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
        string(name: 'CORTX_SERVER_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-rgw:2.0.0-latest', description: 'CORTX-SERVER image', trim: true)
        string(name: 'CORTX_DATA_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-data:2.0.0-latest', description: 'CORTX-DATA image', trim: true)
        string(name: 'CORTX_CONTROL_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-control:2.0.0-latest', description: 'CORTX-CONTROL image', trim: true)
        choice(
            name: 'DEPLOYMENT_METHOD',
            choices: ['standard', 'data-only'],
            description: 'Method to deploy required CORTX service. standard method will deploy all CORTX services'
        )
        string(name: 'SNS_CONFIG', defaultValue: '1+0+0', description: 'sns configuration for deployment. Please select value based on disks available on nodes.', trim: true)
        string(name: 'DIX_CONFIG', defaultValue: '1+0+0', description: 'dix configuration for deployment. Please select value based on disks available on nodes.', trim: true)
        string(name: 'CONTROL_EXTERNAL_NODEPORT', defaultValue: '31169', description: 'Port to be used for control service.', trim: true)
        string(name: 'S3_EXTERNAL_HTTP_NODEPORT', defaultValue: '30080', description: 'HTTP Port to be used for IO service.', trim: true)
        string(name: 'S3_EXTERNAL_HTTPS_NODEPORT', defaultValue: '30443', description: 'HTTPS to be used for IO service.', trim: true)
        string(name: 'NAMESPACE', defaultValue: 'cortx', description: 'kubernetes namespace to be used for CORTX deployment.', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VM details to be used. First node will be used as Primary node', name: 'hosts')
        choice(
            name: 'EXTERNAL_EXPOSURE_SERVICE',
            choices: ['NodePort', 'LoadBalancer'],
            description: 'K8s Service to be used to expose RGW Service to outside cluster.'
        )
        string(name: 'WHITESOURCE_SERVER_URL', defaultValue: 'https://saas.whitesourcesoftware.com', description: 'WhiteSource Server URL', trim: true)
        string(name: 'API_KEY', defaultValue: ' ', description: 'Api Key', trim: true)
        string(name: 'USER_KEY', defaultValue: ' ', description: 'User Key', trim: true)
        string(name: 'PRODUCT_NAME', defaultValue: 'cortx-all-k8s-cluster', description: 'Product name', trim: true)
        string(name: 'DOCKER_REGISTRY', defaultValue: 'ghcr.io/seagate', description: 'Docker registry', trim: true)
        string(name: 'WHITESOURCE_VERSION', defaultValue: '20.11.1', description: 'MainPod & WorkerPod version', trim: true)
        password(name: 'PULL_SECRET', description: 'Image pull secret')
  
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

        stage ('Setup K8s Cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Setup K8s Cluster', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        if [ "$(cat hosts | wc -l)" -eq 2 ]
                        then
                           echo "Current configuration does not support 2 node CORTX cluster deployment. Please try with 1 or more than two nodes."
                           echo "Exiting Jenkins job."
                           exit 1
                        fi
                        ./cluster-setup.sh
                    popd
                '''
            }
        }

        stage ('Deploy CORTX') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploy CORTX Components', script: '''
                    pushd solutions/kubernetes/
                        export SOLUTION_CONFIG_TYPE=automated
                        export CORTX_SCRIPTS_BRANCH=${CORTX_SCRIPTS_BRANCH}
                        export CORTX_SCRIPTS_REPO=${CORTX_SCRIPTS_REPO}
                        export CORTX_SERVER_IMAGE=${CORTX_SERVER_IMAGE}
                        export CORTX_DATA_IMAGE=${CORTX_DATA_IMAGE}
                        export CORTX_CONTROL_IMAGE=${CORTX_CONTROL_IMAGE}
                        export DEPLOYMENT_METHOD=${DEPLOYMENT_METHOD}
                        export SNS_CONFIG=${SNS_CONFIG}
                        export DIX_CONFIG=${DIX_CONFIG}
                        export EXTERNAL_EXPOSURE_SERVICE=${EXTERNAL_EXPOSURE_SERVICE}
                        export CONTROL_EXTERNAL_NODEPORT=${CONTROL_EXTERNAL_NODEPORT}
                        export S3_EXTERNAL_HTTP_NODEPORT=${S3_EXTERNAL_HTTP_NODEPORT}
                        export S3_EXTERNAL_HTTPS_NODEPORT=${S3_EXTERNAL_HTTPS_NODEPORT}
                        export NAMESPACE=${NAMESPACE}
                        ./cortx-deploy.sh --cortx-cluster
                    popd
                '''
            }
        }

        stage ('WhiteSource container scan') {
           steps {
                sh label: 'WhiteSource container scanning', script: '''
                        pushd scripts/security/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        export SOLUTION_CONFIG_TYPE=automated
                        export WHITESOURCE_SERVER_URL=${WHITESOURCE_SERVER_URL}
                        export API_KEY=${API_KEY}
                        export USER_KEY=${USER_KEY}
                        export PRODUCT_NAME=${PRODUCT_NAME}
                        export DOCKER_REGISTRY=${DOCKER_REGISTRY}
                        export WHITESOURCE_VERSION=${WHITESOURCE_VERSION}
                        export PULL_SECRET=${PULL_SECRET}
                        ./whitesource_container_scan.sh
                    popd
                '''
            }
        }
    }
}