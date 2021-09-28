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
            choices: ['stable', 'main', 'cortx-1.0'],
            description: 'Branch Name'
        )
        
	}	
	

    stages {

        stage('Checkout Script') {
            steps {             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re/']]])                
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
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="rpm_validation.html"}',
                    subject: 'RPM Validation Result - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    to: 'cortx.sme@seagate.com, shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, amol.j.kongre@seagate.com, mukul.malhotra@seagate.com'
                }
            } 
        }
    }
}
