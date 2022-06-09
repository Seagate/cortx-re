pipeline {
    agent {
        node {
            label 'docker-k8-deployment-node'
        }
    }
    
    options {
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_TOOLS_BRANCH', defaultValue: 'main', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_TOOLS_REPO', defaultValue: 'Seagate/seagate-tools', description: 'Repository for Cluster Setup scripts', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'CORTX Cluster Primary node details', name: 'primary_nodes')
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'Client node details', name: 'client_nodes')
    }

    environment {
        GITHUB_CRED = credentials('shailesh-github-token')
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

        stage ('Execute performace sanity') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Execute performace sanity', script: '''
                    pushd scripts/performance
                        echo $client_nodes | tr ' ' '\n' > client_nodes
                        echo $primary_nodes | tr ' ' '\n' > primary_nodes
                        export GITHUB_TOKEN=${GITHUB_CRED}
                        export CORTX_TOOLS_REPO=${CORTX_TOOLS_REPO}
                        export CORTX_TOOLS_BRANCH=${CORTX_TOOLS_BRANCH}
                        ./run-performace-tests.sh
                    popd
                '''
            }
        }
    }
}
