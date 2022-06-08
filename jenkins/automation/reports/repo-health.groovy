pipeline {
    agent {
        node {
            label 'docker-k8-deployment-node'
        }
    }

    options {
        timeout(time: 420, unit: 'MINUTES')
        timestamps()
    }
    
    environment {
        GH_OATH = credentials('shailesh-github-token')
        CODACY_OATH = credentials('shailesh-codacy-auth')
    }

    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'CORTX RE Branch or GitHash to Generate Repo Health Report', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'CORTX RE Repository to to Generate Repo Health Report', trim: true)
        string(name: 'CORTX_BRANCH', defaultValue: 'main', description: 'CORTX Branch or GitHash to Generate Repo Health Report', trim: true)
        string(name: 'CORTX_REPO', defaultValue: 'https://github.com/Seagate/cortx', description: 'CORTX Repository to to Generate Repo Health Report', trim: true)
    }    

    stages {

        stage('Checkout Script') {
            steps {
                script {
                    checkout poll: false, scm: [$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[url: "${CORTX_RE_REPO}"]]]
                }
            }
        }

        stage('Generate Report') {
            steps {
                script {
                    sh '''
                        pushd scripts/reports
                            export CORTX_REPO=${CORTX_REPO}
                            export CORTX_BRANCH=${CORTX_BRANCH}
                            sh ./repo-health.sh
                        popd
                    '''
                }
            }
        }
    }

    post {
        always {
            script { 
               echo 'Cleanup Workspace.'
                archiveArtifacts artifacts: "scripts/reports/cortx/metrics/report/*.html, scripts/reports/cortx/metrics/cache/repo_health.pdf", onlyIfSuccessful: false, allowEmptyArchive: true
                
                echo 'Cleanup Workspace.'
                deleteDir() /* clean up our workspace */
            }
        }
    }        
}