pipeline {
    agent {
        node {
           label "docker-centos-7.9.2009-node"
        }
    }
    
    options {
		timeout(time: 240, unit: 'MINUTES')
		timestamps()
		disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
		ansiColor('xterm')
	}

    parameters {

        string(name: 'MULTISITE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for cortx-multisite', trim: true)
        string(name: 'MULTISITE_REPO', defaultValue: 'https://github.com/Seagate/cortx-multisite/', description: 'Repository URL for multisite', trim: true)
	}	

    stages {

        stage('Checkout Script') {
            steps {             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${MULTISITE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${MULTISITE_REPO}"]]])                
                }
            }
        }
        
        stage ('Execute Test') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Execute Test', script: '''
                pushd ./s3/replication
                    sh ./build_and_test.sh
                popd
                '''
			}
		}	
    }

	post {

		always {
			script {

				def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                
                def mailRecipients = "shailesh.vaidya@seagate.com"
                emailext ( 
                    body: '''${SCRIPT, template="release-email.template"}''',
                    mimeType: 'text/html',
                    subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                    attachLog: true,
                    to: "${mailRecipients}",
					recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}