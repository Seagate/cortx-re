#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
           label 'docker-image-builder-centos-7.9.2009'
        }
    }
	
    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        disableConcurrentBuilds()   
    }

    environment {
	    GITHUB_CRED = credentials('shailesh-github')
	}

    parameters {  
        string(name: 'CORTX_RE_URL', defaultValue: 'https://github.com/Seagate/cortx-re.git', description: 'Repository URL for cortx-all image build.')
		string(name: 'CORTX_RE_BRANCH', defaultValue: 'kubernetes', description: 'Branch for cortx-all image build.')
		string(name: 'BUILD_URL', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx/github/main/centos-7.8.2003/last_successful_prod/', description: 'Build URL for cortx-all docker image')
	}	
    
    stages {
	
		stage('Prerequisite') {
            steps {
                sh encoding: 'utf-8', label: 'Validate Docker pre-requisite', script: """
                   systemctl status docker
                   /usr/local/bin/docker-compose --version
                """
			}
		}
	
        stage('Checkout') {
            steps {
				script { build_stage = env.STAGE_NAME }
                checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'PathRestriction', excludedRegions: '', includedRegions: 'scripts/third-party-rpm/.*']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_URL}"]]])
            }
        }

        stage ('GHCR Login') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'GHCR Login', script: '''
                docker login ghcr.io -u ${GITHUB_CRED_USR} -p ${GITHUB_CRED_PSW}
                '''
			}
		}

        stage('Build & push Image') {
            steps {
				script { build_stage = env.STAGE_NAME }
                sh encoding: 'utf-8', label: 'Build cortx-all docker image', script: """
                    pushd ./docker/cortx-deploy
                        sh ./build.sh $BUILD_URL
                    popd
                    docker logout  
                """
            }
        }
	}

    post {

		always {
			script {
				env.release_build_location = "https://github.com/Seagate/cortx-re/pkgs/container/cortx-all"
				env.release_build = "${env.release_tag}"
				env.build_stage = "${build_stage}"
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