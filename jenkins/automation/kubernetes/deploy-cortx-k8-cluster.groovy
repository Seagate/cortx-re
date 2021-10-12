pipeline {
    agent {
        node {
            label 'docker-k8-deployment-node'
        }
    }
    
    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    environment {
        GITHUB_CRED = credentials('shailesh-github-token')
    }


    parameters {

        string(name: 'CORTX_RE_BRANCH', defaultValue: 'kubernetes', description: 'Branch or GitHash for CORTX Cluster scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for CORTX Cluster scripts', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VM details to be used. First node will be used as Master', name: 'hosts')

        choice(
			name: 'DEPLOY_TARGET',
			choices: ['THIRD-PARTY-ONLY', 'CORTX-CLUSTER'],
			description: 'Deployment Target THIRD-PARTY-ONLY - This will only install third party components, CORTX-CLUSTER - This will install Third party and CORTX components both.'
		)
       
    }    

    stages {

        stage('Checkout Script') {
            steps { 
                cleanWs()            
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])                
                }
            }
        }

		stage ("Setup K8 Cluster") {
			steps {
				script { build_stage = env.STAGE_NAME }
				script {
					try {
						def cortx_utils_build = build job: 'setup-kubernetes-cluster', wait: true,
										parameters: [
											string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
											string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
											string(name: 'hosts', value: "${hosts}")
										]
					} catch (err) {
						build_stage = env.STAGE_NAME
						error "Failed to Setup K8 Cluster"
					}
				}                        
			}
		}

		stage ("Deploy third-party components") {
            when {
                expression { params.DEPLOY_TARGET == 'THIRD-PARTY-ONLY' }
            }
			steps {
				script { build_stage = env.STAGE_NAME }
				script {
					try {
						def cortx_utils_build = build job: 'install-third-party-components', wait: true,
										parameters: [
											string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
											string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
											string(name: 'hosts', value: "${hosts}")
										]
					} catch (err) {
						build_stage = env.STAGE_NAME
						error "Deploy third-party components"
					}
				}                        
			}
		}

        stage ("Deploy CORTX components") {
            when {
                expression { params.DEPLOY_TARGET == 'CORTX-CLUSTER' }
            }
			steps {
				script { build_stage = env.STAGE_NAME }
				script {
					try {
						def cortx_utils_build = build job: 'setup-cortx-cluster', wait: true,
										parameters: [
											string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
											string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
											string(name: 'hosts', value: "${hosts}")
										]
					} catch (err) {
						build_stage = env.STAGE_NAME
						error "Deploy CORTX components"
					}
				}                        
			}
		}
    }

    post {
        always {

            script {

                // Jenkins Summary
                clusterStatus = ""
                if ( currentBuild.currentResult == "SUCCESS" ) {
                    //clusterStatus = readFile(file: '/var/tmp/cortx-cluster-status.txt')
                    MESSAGE = "CORTX Cluster Setup Success for the build ${build_id}"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                } else if ( currentBuild.currentResult == "FAILURE" ) {
                    manager.buildFailure()
                    MESSAGE = "CORTX Cluster Setup Failed for the build ${build_id}"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
 
                } else {
                    manager.buildUnstable()
                    MESSAGE = "CORTX Cluster Setup is Unstable for the build ${build_id}"
                    ICON = "warning.gif"
                    STATUS = "UNSTABLE"
                }
                
                clusterStatusHTML = "<pre>${clusterStatus}</pre>"

                manager.createSummary("${ICON}").appendText("<h3>CORTX Cluster Setup ${currentBuild.currentResult} </h3><p>Please check <a href=\"${BUILD_URL}/console\">cluster setup logs</a> for more info <h4>Cluster Status:</h4>${clusterStatusHTML}", false, false, false, "red")

                // Email Notification
                env.cluster_status = "${clusterStatusHTML}"
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                mailRecipients = "shailesh.vaidya@seagate.com"
                emailext ( 
                    body: '''${SCRIPT, template="cluster-setup-email.template"}''',
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