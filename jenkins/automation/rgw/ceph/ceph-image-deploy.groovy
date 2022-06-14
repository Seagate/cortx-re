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
        disableConcurrentBuilds()   
    }

    parameters {
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VM details to be used. Currently only single node is supported for image deployment.', name: 'hosts')
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

        stage ('Deploy Ceph Prerequisites') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Install Ceph Prerequisites', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        bash ceph-deploy.sh --install-prereq
                    popd
                '''
            }
        }

        stage ('Deploy Ceph') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Install Ceph Packages', script: '''
                    pushd solutions/kubernetes/
                        bash ceph-deploy.sh --install-ceph
                    popd
                '''
            }
        }

        stage ('IO Operation') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'IO Operation', script: '''
                    pushd solutions/kubernetes/
                        bash ceph-deploy.sh --io-operation
                    popd
                '''
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}