pipeline {
    agent {
        node {
            label 'k8-executor'
        }
    }
    
    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    environment {
        GITHUB_CRED = credentials('shailesh-github-token')
    }


    parameters {

        string(name: 'CORTX_RE_BRANCH', defaultValue: 'kubernetes', description: 'Branch or GitHash for Cluster Destroy scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Destroy scripts', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VM details to be used. First node will be used as Master', name: 'hosts')
       
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

		stage ("Destroy CORTX Cluster") {
			steps {
				script { build_stage = env.STAGE_NAME }
				script {
					try {
						def cortx_utils_build = build job: 'destroy-cluster', wait: true,
										parameters: [
											string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
											string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
											string(name: 'hosts', value: "${hosts}")
										]
					} catch (err) {
						build_stage = env.STAGE_NAME
						error "destroy-cluster"
					}
				}                        
			}
		}

		stage ("Deploy third-party components") {
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
                if ( fileExists('/var/tmp/cortx-cluster-status.txt') && currentBuild.currentResult == "SUCCESS" ) {
                    clusterStatus = readFile(file: '/var/tmp/cortx-cluster-status.txt')
                    MESSAGE = "CORTX Cluster Destroy Success for the build ${build_id}"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                } else if ( currentBuild.currentResult == "FAILURE" ) {
                    manager.buildFailure()
                    MESSAGE = "CORTX Cluster Destroy Failed for the build ${build_id}"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
 
                } else {
                    manager.buildUnstable()
                    MESSAGE = "CORTX Cluster Destroy is Unstable"
                    ICON = "warning.gif"
                    STATUS = "UNSTABLE"
                }
                
                clusterStatusHTML = "<pre>${clusterStatus}</pre>"

                manager.createSummary("${ICON}").appendText("<h3>CORTX Cluster Destroy ${currentBuild.currentResult} </h3><p>Please check <a href=\"${BUILD_URL}/console\">cluster setup logs</a> for more info <h4>Cluster Status:</h4>${clusterStatusHTML}", false, false, false, "red")

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