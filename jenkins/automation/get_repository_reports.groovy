pipeline {
    agent {
        node {
            label 'docker-centos-7.9.2009-node'
        }
    }

    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps() 
    }

    parameters {
        choice(
            name: 'Repository', 
            choices: ['ALL', 'cortx-s3server', 'cortx-motr', 'cortx-hare', 'cortx-ha', 'cortx-prvsnr', 'cortx-sspl', 'cortx-manager', 'cortx-management-portal', 'cortx-utils', 'cortx-monitor', 'cortx-multisite'],
            description: 'Repo Name'
        )
	}	
	

    stages {

        stage('Checkout Script') {
            steps {             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: 'git-repository-reports-936455']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/AbhijitPatil1992/cortx-re/']]])                
                }
            }
        }

        stage('Generate Report') {
            steps {
                script {
                    sh '''
                        if [ $Repository = "ALL" ]; then
                            Repository="cortx-s3server,cortx-motr,cortx-hare,cortx-ha,cortx-prvsnr,cortx-sspl,cortx-manager,cortx-management-portal,cortx-utils,cortx-monitor,cortx-multisite"
                        fi
                        gituser="Seagate"
                        cp scripts/automation/get_repo_details.sh /opt/
                        pushd /opt
                        bash get_repo_details.sh $Repository $gituser
                        cp /opt/Repository_reports.csv $WORKSPACE/
                    '''
                }
            }
        }

		stage('Send Email') {
            steps {             
                script {
                    toEmail = "abhijit.patil@seagate.com,priyank.p.dalal@seagate.com"
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext attachmentsPattern: 'Repository_reports.csv', mimeType: 'text/html',
                    body: '''Hi Team,<br/><br/>
                    
                    Please find an attachment for repositories report.<br/><br/>
                    
                    Thanks,<br/>
                    DevOps Team.<br/>''',
                    subject: 'Repository Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    to: toEmail
                }
            } 
        }
        
    }
}
