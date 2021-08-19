#!/usr/bin/env groovy
pipeline {

        agent {
                node {
                        label 'docker-cp-centos-7.8.2003-node'
                }
        }

        parameters {
                string(name: 'RELEASE_INFO_URL', defaultValue: '', description: 'RELEASE BUILD')
                string(name: 'GIT_TAG', defaultValue: '', description: 'Tag Name')
		string(name: 'TAG_MESSAGE', defaultValue: '', description: 'Tag Message')
		booleanParam(name: 'DEBUG', defaultValue: false, description: 'Select this if you want to Delete the current Tag')
            }
		
		
        stages {
		
	stage('Checkout Script') {
            steps {             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])                
                }
            }
        }
	stage ("Tagging") {
	    steps {
		script { build_stage=env.STAGE_NAME }
			script { 
				withCredentials([usernamePassword(credentialsId: 'cortx-admin-github', passwordVariable: 'PASSWD', usernameVariable: 'USER_NAME')]) {
				sh label: 'Git-Tagging', script: '''sh scripts/release_support/git-tag.sh $RELEASE_INFO_URL $GIT_TAG $TAG_MESSAGE'''					
					}
				}			
			}
		}
	}
}

