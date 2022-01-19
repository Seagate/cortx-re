pipeline {
    agent {
        node {
            label 'k8-executor'
        }
    }
    
    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }


    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'NODE', defaultValue: '', description: 'Node 1 Host FQDN',  trim: true)
        password(name: 'NODE_PASS', defaultValue: '', description: 'Host machine root user password')
    }
    environment {
        USER = "root"
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


        stage ('Setup Cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Cluster Setup', script: '''
                    pushd solutions/kubernetes/
                        ./minikube-setup.sh ${NODE} ${USER} ${NODE_PASS}
                    popd
                '''
            }
        }
    }
}