pipeline {

	agent {
		node {
				label 'build-retention'
		}
	}
	parameters {

		string(name: 'BUILD', defaultValue: '"561","531","2750"', description: 'Build Number')
		string(name: 'BRANCH', defaultValue: '"main","custom-ci"', description: 'Branch Name')
		string(name: 'OS', defaultValue: '"centos-7.8.2003","centos-7.9.2009","rhel-7.7.1908"', description: 'OS Version')


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

					sh label: 'Cleanup', script: '''sudo sh -x scripts/build-retention/cleanup.sh $BUILD $BRANCH $OS'''
					SPACE = "${sh(script: 'df -h | grep /mnt/bigstorage/releases', returnStdout: true).trim()}"

				}			
			}
		}
		
		stage('Send Email') {
            steps {             
                script {
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext (
    			    body: "Current Disk Space is ${SPACE} : Job ${env.JOB_NAME} : Build URL ${env.BUILD_URL}",
    			    subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME} : build ${env.BUILD_NUMBER}",
                    to: 'CORTX.DevOps.RE@seagate.com'
                    )
                }
            } 
        }
		
	}
}

