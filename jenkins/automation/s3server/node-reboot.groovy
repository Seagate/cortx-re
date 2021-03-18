#!/usr/bin/env groovy
pipeline { 
    agent { label 'docker-cp-centos-7.8.2003-node' }

    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        buildDiscarder(logRotator(numToKeepStr: "30"))
    }

    stages {

        // Clone deploymemt scripts from cortx-re repo
        stage ('Checkout Scripts') {
            steps {
                script {
					// Clone cortx-re repo
                    dir('cortx-re') {
                        checkout([$class: 'GitSCM', branches: [[name: 's3-node-reboot']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/shailesh-vaidya/cortx-re']]])                
                    }
                }
            }
        }
		
		stage('Install prerequisite') {
		steps {
			sh label: '', returnStatus: true, script: '''
				yum install ansible -y
			'''
			}
		}

        stage('Reboot Nodes') {
            steps {
                script {
				   withCredentials([usernamePassword(credentialsId: 'node-user', passwordVariable: 'USER_PASS', usernameVariable: 'USER_NAME')]) {
						dir("cortx-re/scripts/automation/server-reboot/") {
							ansiblePlaybook(
								playbook: 'reboot.yml',
								inventory: 'hosts',
								extraVars: [
									"ansible_ssh_pass"                 : [value: "${USER_PASS}", hidden: false],
								],
								extras: '-v',
								colorized: true
							)
						}                        
					}
				}
            }
        }
	}
}