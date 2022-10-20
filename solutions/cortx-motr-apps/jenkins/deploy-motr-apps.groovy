pipeline {
    agent {
        node {
            label 'cortx-motr-apps-node'
        }
    }
    
    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    environment {
        GITHUB_CRED = credentials('shailesh-github-token')
    }

    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Setup scripts', trim: true)
        string(name: 'CORTX_MOTR_REPO', defaultValue: 'seagate/cortx-motr', description: 'Repository for Motr Release', trim: true)
        string(name: 'CORTX_MOTR_RELEASE', defaultValue: 'latest', description: 'Motr Release', trim: true)
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

        stage ('Setup CORTX Repository') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Setup CORTX rpm packages repository', script: '''
                    pushd scripts/release_support
                        ./set-local-repo.sh $CORTX_MOTR_REPO $CORTX_MOTR_RELEASE
                    popd
                '''
            }
        }


        stage ('Deploy Motr Apps') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploy Motr Apps', script: '''
                    pushd solutions/cortx-motr-apps/scripts/
                        bash ./deploy-motr-apps.sh
                    popd
                '''
            }
        }
    }
}