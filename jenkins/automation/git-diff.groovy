pipeline {
    agent {
        node {
           label "cortx-test-ssc-vm-4090"
        }
    }

    stages {
	    stage('Checkout source code') {
            steps {
                script {
                    checkout([$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, userRemoteConfigs: [[credentialsId: 'github-access', url: 'https://github.com/seagate/cortx-re/']]])
					sh 'cp ./scripts/automation/git-diff/* .'
                }
            }
        }
        stage('Checkout repositories') {
            steps {
				cleanWs()
				script {
					def projects = readJSON file: "${env.WORKSPACE}/config.json"
		            echo "${env.WORKSPACE}/scripts/automation/git-diff/config.json"
					projects.repository.each { entry ->
						echo entry.name
						def repourl = 'https://github.com/seagate/' + entry.name
						stage ('Checkout Repo:' + entry.name) {
							dir (entry.name) {
								timestamps {
									checkout([$class: 'GitSCM', branches: [[name: "main"]], doGenerateSubmoduleConfigurations: false, userRemoteConfigs: [[credentialsId: 'github-access', url: repourl]]])
								}
							}
						}
					}
                }
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
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="git_diff.html"}',
                    subject: 'Third Party Depdenency scan - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    to: 'venkatesh.k@seagate.com, shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com'
                }
            }
        }
	}
}

