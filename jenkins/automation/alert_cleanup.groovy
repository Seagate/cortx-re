#!/usr/bin/env groovy
pipeline {

	agent {
		node {
			label 'build_retention_node'
		}
	}
	parameters {
	        string(name: 'PATH1', defaultValue: '/mnt/data1/releases/cortx/github/cortx-1.0/centos-7.8.2003', description: 'Build Path1')
		string(name: 'PATH2', defaultValue: '/mnt/data1/releases/cortx/github/release/rhel-7.7.1908', description: 'Build Path2')
		string(name: 'BUILD', defaultValue: "", description: 'BuildNumber')
		choice(name: 'BRANCH', choices: ["main", "stable"], description: 'Branch to be deleted')
		choice(name: 'OS', choices: ["centos-7.8.2003", "centos-7.7.1908", "centos-7.9.2009"], description: 'OS Version')

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
		stage('Cleanup') {
		    steps {
			script { 
			withCredentials([usernamePassword(credentialsId: 'cortx-admin-github', passwordVariable: 'PASSWD', usernameVariable: 'USER_NAME')]) {
			sh label: 'Cleanup', script: '''sudo sh -x scripts/release_support/cleanup.sh $PATH1 $PATH2 $BUILD $BRANCH $OS'''
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

