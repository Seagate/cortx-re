pipeline {
	agent {
        node {
           label "docker-${os_version}-node"
           // label "cortx-test-ssc-vm-4090"
        }
    }
    parameters {
        string(
            defaultValue: 'centos-7.9.2009',
            name: 'os_version',
            description: 'OS version of the build',
            trim: true
        )
		string(
            defaultValue: 'dev',
            name: 'environment',
            description: 'Environment',
            trim: true
        )
    }

    stages {
	    stage('Checkout source code') {
            steps {
                script {
                    checkout([$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/seagate/cortx-re/']]])
					sh 'cp ./scripts/automation/git-diff/* .'
                }
            }
        }
        stage('Checkout repositories') {
            steps {
				// cleanWs()
				script {
					def projects = readJSON file: "${env.WORKSPACE}/config.json"
		            echo "${env.WORKSPACE}/scripts/automation/git-diff/config.json"
					projects.repository.each { entry ->
						echo entry.name
						def repourl = 'https://github.com/seagate/' + entry.name
						stage ('Checkout Repo:' + entry.name) {
							dir (entry.name) {
								timestamps {
									checkout([$class: 'GitSCM', branches: [[name: "main"]], doGenerateSubmoduleConfigurations: false, userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: repourl]]])
								}
							}
						}
					}
                }
            }
        }
        stage('Install Dependencies') {
            steps {
                sh label: 'Installed Dependencies', script: '''
                    yum install -y GitPython.noarch
                '''
            }
        }

		stage('Get Git Diff') {
            steps {
                script {
					echo "Checking the difference in packages file.."
                    sh "python git_diff.py -d Last-Day -c config.json"
                }
            }
        }

		stage('Send Email') {
            steps {
                script {
					def useEmailList = ''
					if ( params.environment == 'prod') {
						useEmailList = 'cortx.sme@seagate.com, shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, mukul.malhotra@seagate.com'
					}
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="git_diff.html"}',
                    subject: 'Third Party Depdenency scan - [ Date :' + new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
					to: useEmailList
                }
            }
        }
	}
}

