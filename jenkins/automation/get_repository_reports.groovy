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
                    checkout([$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
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
                        cp scripts/automation/git_repo_reports.sh /opt/
                        pushd /opt
                        bash git_repo_reports.sh $Repository $gituser
                        cp /opt/Repository_reports.csv $WORKSPACE/
                    '''
                }
            }
        }
        stage('Send Email') {
            steps {
                script {
                    env.ForEmailPlugin = env.WORKSPACE
					emailext attachmentsPattern: 'Repository_reports.csv', mimeType: 'text/html',
                    body: '''Hi Team,<br/><br/>

                    Please find an attachment for repositories report.<br/><br/>

                    Thanks,<br/>
                    DevOps Team.<br/>''',
                    subject: 'Repository Report - [ Date :' + new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']]
                }
            }
        }
    }
}
