pipeline {
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }
    
    options {
        timeout(time: 15, unit: 'MINUTES')
        timestamps() 
    }

    parameters {
        choice(
            name: 'branch', 
            choices: ['main', 'stable', 'cortx-1.0'],
            description: 'Branch Name'
        )
        choice (
            choices: ['ALL', 'DEBUG'],
            description: 'Email Notification Recipients ',
            name: 'EMAIL_RECIPIENTS'
        )
    }    
    
    stages {

        stage('Checkout Script') {
            steps {             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])                
                }
            }
        }

        stage('Generate Report') {
            steps {             
                script {    
                    sh "bash scripts/release_support/rpm-validator.sh $branch $os_version"
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: false, reportDir: '', reportFiles: 'rpm_validation.html', reportName: 'RMP Check', reportTitles: ''])
                }
            }
        }

        stage('Send Email') {
            steps {             
                script {

                    if ( params.EMAIL_RECIPIENTS == "ALL" ) {
                        mailRecipients = 'cortx.sme@seagate.com, CORTX.DevOps.RE@seagate.com, shailesh.vaidya@seagate.com, amol.j.kongre@seagate.com, mukul.malhotra@seagate.com'
                    } else if ( params.EMAIL_RECIPIENTS == "DEBUG" ) {
                        mailRecipients = "shailesh.vaidya@seagate.com"
                    }
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="rpm_validation.html"}',
                    subject: 'RPM Validation Result - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    to: "${mailRecipients}",
                    recipientProviders: [[$class: 'RequesterRecipientProvider']]
                }
            } 
        }
    }
}
