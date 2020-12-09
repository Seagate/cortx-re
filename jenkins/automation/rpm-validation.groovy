pipeline {
    agent {
        node {
            label 'docker-io-centos-7.8.2003-node'
        }
    }
    
    options {
        timeout(time: 15, unit: 'MINUTES')
        timestamps() 
    }

    stages {

        stage('Checkout Script') {
            steps {             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: 'rpm-validation-fix']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re/']]])                
                }
            }
        }

        stage('Generate Report') {
            steps {             
                script {    
                    sh "bash scripts/rpm_validation/rpm-validator.sh"
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: '', reportFiles: 'rpm_validation.html', reportName: 'RMP Check', reportTitles: ''])
                }
            }
        }

		stage('Send Email') {
            steps {             
                script {
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="rpm_validation.html"}',
                    subject: 'RPM Validation Result - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    to: 'shailesh.vaidya@seagate.com'
                }
            } 
        }
    }
}