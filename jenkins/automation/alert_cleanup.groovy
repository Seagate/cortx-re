#!/usr/bin/env groovy
pipeline {

	agent {
		node {
			label 'build_retention_node'
		}
	}
	parameters {

		string(name: 'BUILD', defaultValue: "", description: 'BuildNumber')
		string(name: 'BRANCH', defaultValue: "", description: 'Branch Name')
		string(name: 'OS', defaultValue: "", description: 'OS Version')


    }
	
	triggers {
         cron('0 */6 * * *')
    }
	stages {
		
	stage('Checkout Script') {
            steps {             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])   
                }
            }
        }
	stage ('Cleanup') {
		steps {
			script { 
				withCredentials([usernamePassword(credentialsId: 'cortx-admin-github', passwordVariable: 'PASSWD', usernameVariable: 'USER_NAME')]) {
				sh label: 'Cleanup', script: '''sudo sh -x scripts/release_support/cleanup.sh $BUILD $BRANCH $OS'''
				SPACE = "${sh(script: 'df -h | grep /mnt/data1/releases', returnStdout: true).trim()}"
					}
				}			
			}
		}
		
	}

	post {
		always {
			script {
		        emailext (
				body: "Current Disk Space is ${SPACE} : Job ${env.JOB_NAME} : Build URL ${env.BUILD_URL}",
				subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME} : build ${env.BUILD_NUMBER}",
				to: "CORTX.DevOps.RE@seagate.com",
		        )
	
		}	
	}
    }
}

