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
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_SERVER_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-rgw:2.0.0-latest', description: 'CORTX-SERVER image', trim: true)
        string(name: 'CORTX_DATA_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-data:2.0.0-latest', description: 'CORTX-DATA image', trim: true)
        string(name: 'CORTX_CONTROL_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-control:2.0.0-latest', description: 'CORTX-CONTROL image', trim: true)
        choice(
            name: 'POD_TYPE',
            choices: ['all', 'data', 'control', 'ha', 'server'],
            description: 'Method to deploy required CORTX service. standard method will deploy all CORTX services'
        )
        choice(
            name: 'DEPLOYMENT_METHOD',
            choices: ['standard', 'data-only'],
            description: 'Method to deploy required CORTX service. standard method will deploy all CORTX services'
        )
        // Please configure hosts, CORTX_SCRIPTS_BRANCH and CORTX_SCRIPTS_REPO parameter in Jenkins job configuration.
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

        stage('Pre-Upgrade Cluster Status') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'fetch cluster status', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' | head -n 1 > hosts
                        cat hosts
                        ./cortx-upgrade.sh --cluster-status
                    popd
                '''
            }
        }

        stage('Upgrade Cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Upgrade cluster', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' | head -n 1 > hosts
                        cat hosts
                        export CORTX_SCRIPTS_BRANCH=${CORTX_SCRIPTS_BRANCH}
                        export CORTX_SCRIPTS_REPO=${CORTX_SCRIPTS_REPO}
                        export CORTX_SERVER_IMAGE=${CORTX_SERVER_IMAGE}
                        export CORTX_DATA_IMAGE=${CORTX_DATA_IMAGE}
                        export CORTX_CONTROL_IMAGE=${CORTX_CONTROL_IMAGE}
                        export POD_TYPE=${POD_TYPE}
                        export DEPLOYMENT_METHOD=${DEPLOYMENT_METHOD}
                        ./cortx-upgrade.sh --rolling-upgrade
                    popd
                '''
            }
        }

        stage('Post-Upgrade Cluster Status') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'fetch cluster status', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' | head -n 1 > hosts
                        cat hosts
                        ./cortx-upgrade.sh --cluster-status
                    popd
                '''
            }
        }

    }
}        