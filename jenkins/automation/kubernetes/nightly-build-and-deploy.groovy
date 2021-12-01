pipeline {
	agent {
		node {
			label 'docker-centos-7.9.2009-node'
		}
	}
	options {
		timeout(time: 600, unit: 'MINUTES')
		timestamps()
		buildDiscarder(logRotator(daysToKeepStr: '20', numToKeepStr: '20'))
		disableConcurrentBuilds()
	}
	triggers { cron('30 19 * * *') }
	environment {
		CORTX_RE_BRANCH = "kubernetes"
		CORTX_RE_REPO = "https://github.com/Seagate/cortx-re/"
		DOCKER_IMAGE_LOCATION = "https://github.com/Seagate/cortx-re/pkgs/container/cortx-all"
	}
	parameters {
		string(name: 'COMPONENT_BRANCH', defaultValue: 'kubernetes', description: 'Component Branch.')
		choice (
			choices: ['DEBUG', 'DEVOPS', 'ALL'],
			description: 'Email Notification Recipients ',
			name: 'EMAIL_RECIPIENTS'
		)
		string(name: 'CORTX_SCRIPTS_BRANCH', defaultValue: 'v0.0.14', description: 'Release for cortx-k8s scripts (Services Team)', trim: true)
		string(name: 'CORTX_SCRIPTS_REPO', defaultValue: 'Seagate/cortx-k8s', description: 'Repository for cortx-k8s scripts (Services Team)', trim: true)
		string(name: 'ADMIN_USER', defaultValue: "cortxadmin", description: 'CSM Admin login username for the given host')
		password(name: 'ADMIN_PWD', defaultValue: 'Cortxadmin@123', description: 'CSM Admin login password for the given host    ')
	}
	// Please configure hosts parameter in Jenkins job configuration.
	stages {
		stage ("Define Variable") {
			steps {
				script { build_stage = env.STAGE_NAME }
				script {
					env.allhost = sh( script: '''
					echo $hosts | tr ' ' '\n' | awk -F["="] '{print $2}'
					''', returnStdout: true).trim()
                
					env.master_node = sh( script: '''
					echo $hosts | tr ' ' '\n' | head -1 | awk -F["="] '{print $2}' | cut -d',' -f1
					''', returnStdout: true).trim()

					env.hostpasswd = sh( script: '''
					echo $hosts | tr ' ' '\n' | head -1 | awk -F["="] '{print $4}'
					''', returnStdout: true).trim()

					env.numberofnodes = sh( script: '''
					echo $hosts | tr ' ' '\n' | tail -n +2 | wc -l
					''', returnStdout: true).trim()

					echo "pass = ${env.hostpasswd}"
				}
			}
		}
		stage ("Build Creation and Cluster Cleanup") {
			parallel {
				stage ("Build Creation") {
					steps {
						script { build_stage = env.STAGE_NAME }
						script {
							def customCI = build job: '/GitHub-custom-ci-builds/centos-7.9/nightly-k8-custom-ci', wait: true,
							parameters: [
								string(name: 'CSM_AGENT_BRANCH', value: "${COMPONENT_BRANCH}"),
								string(name: 'CSM_WEB_BRANCH', value: "${COMPONENT_BRANCH}"),
								string(name: 'HARE_BRANCH', value: "${COMPONENT_BRANCH}"),
								string(name: 'HA_BRANCH', value: "${COMPONENT_BRANCH}"),
								string(name: 'MOTR_BRANCH', value: "${COMPONENT_BRANCH}"),
								string(name: 'PRVSNR_BRANCH', value: "${COMPONENT_BRANCH}"),
								string(name: 'S3_BRANCH', value: "${COMPONENT_BRANCH}"),
								string(name: 'SSPL_BRANCH', value: "${COMPONENT_BRANCH}"),
								string(name: 'CORTX_UTILS_BRANCH', value: "${COMPONENT_BRANCH}"),
								string(name: 'CORTX_RE_BRANCH', value: "${COMPONENT_BRANCH}"),
								string(name: 'THIRD_PARTY_RPM_VERSION', value: "cortx-2.0-k8"),
							]
							env.custom_ci_build_id = customCI.rawBuild.id
						}
					}
				}
				stage ("Cluster Cleanup") {
					steps {
						script { build_stage = env.STAGE_NAME }
						script {
							build job: '/Cortx-kubernetes/destroy-cortx-cluster', wait: true,
							parameters: [
								string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
								string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
								text(name: 'hosts', value: "${hosts}"),
							]
						}
					}
				}
			}
		}
		stage ("CORTX-ALL image creation") {
			steps {
				script { build_stage = env.STAGE_NAME }
				script {
					try {
						def buildCortxDockerImages = build job: '/Cortx-kubernetes/cortx-all-docker-image', wait: true,
						parameters: [
							string(name: 'CORTX_RE_URL', value: "${CORTX_RE_REPO}"),
							string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
							string(name: 'BUILD', value: "kubernetes-build-${env.custom_ci_build_id}"),
							string(name: 'EMAIL_RECIPIENTS', value: "${EMAIL_RECIPIENTS}"),
						]
						env.dockerimage_id = buildCortxDockerImages.buildVariables.image
					} catch (err) {
						build_stage = env.STAGE_NAME
						error "Failed to Build Docker Image"
					}
				}
			}
		}
		stage ("Deploy CORTX Cluster") {
			steps {
				script { build_stage = env.STAGE_NAME }
				script {
					def cortxCluster = build job: '/Cortx-kubernetes/setup-cortx-cluster', wait: true,
					parameters: [
						string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
						string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
						string(name: 'CORTX_IMAGE', value: "${env.dockerimage_id}"),
						text(name: 'hosts', value: "${hosts}"),
						string(name: 'CORTX_SCRIPTS_BRANCH', value: "${CORTX_SCRIPTS_BRANCH}"),
						string(name: 'CORTX_SCRIPTS_REPO', value: "${CORTX_SCRIPTS_REPO}"),
					]
					env.cortxcluster_build_url = cortxCluster.absoluteUrl
					env.cortxCluster_status = cortxCluster.currentResult
				}
			}
		}
/*
		stage ("QA Sanity K8S") {
			steps {
				script {
					catchError(stageResult: 'FAILURE') {
						def qaSanity = build job: '/QA-Sanity-Multinode-K8s', wait: true,
						parameters: [
							string(name: 'M_NODE', value: "${env.master_node}"),
							password(name: 'HOST_PASS', value: "${env.hostpasswd}"),
							string(name: 'ADMIN_USER', value: "${ADMIN_USER}"),
							password(name: 'ADMIN_PWD', value: "${ADMIN_PWD}"),
							string(name: 'CORTX_IMAGE', value: "${env.dockerimage_id}"),
							string(name: 'NUM_NODES', value: "${env.numberofnodes}"),
						]
						env.Sanity_Failed = qaSanity.buildnumber
						env.sanity_result = qaSanity.currentResult
						env.Current_TP = qaSanity.buildVariables.Current_TP
						env.Health = qaSanity.buildVariables.Health
						env.qaSanity_status = qaSanity.currentResult
					}
					copyArtifacts filter: 'log/*report.xml', fingerprintArtifacts: true, flatten: true, optional: true, projectName: 'QA-Sanity-Multinode-K8s', selector: lastCompleted(), target: ''
					copyArtifacts filter: 'log/*report.html', fingerprintArtifacts: true, flatten: true, optional: true, projectName: 'QA-Sanity-Multinode-K8s', selector: lastCompleted(), target: ''
					
				}
			}
		}
*/
	}
	post {
		always {
			script {
//				junit allowEmptyResults: true, testResults: '*report.xml'
				env.qaSanity_status = "UNSTABLE"
				echo "${env.cortxCluster_status}"
				echo "${env.qaSanity_status}"
				if ( "${env.cortxCluster_status}" == "SUCCESS" && "${env.qaSanity_status}" == "SUCCESS" ) {
					MESSAGE = "K8s Build#${build_id} 3node Deployment Deployment=Passed, SanityTest=Passed"
					ICON = "accept.gif"
					STATUS = "SUCCESS"
					env.deployment_result = "SUCCESS"
					env.sanity_result = "SUCCESS"
					currentBuild.result = "SUCCESS"
				} else if ( "${env.cortxCluster_status}" == "SUCCESS" && "${env.qaSanity_status}" == "FAILURE" ) {
					manager.buildFailure()
					MESSAGE = "K8s Build#${build_id} 3node Deployment Deployment=Passed, SanityTest=failed"
					ICON = "error.gif"
					STATUS = "FAILURE"
					env.sanity_result = "FAILURE"
					env.deployment_result = "SUCCESS"
					currentBuild.result = "FAILURE"
				} else if ( "${env.cortxCluster_status}" == "SUCCESS" && "${env.qaSanity_status}" == "UNSTABLE" ) {
					MESSAGE = "K8s Build#${build_id} 3node Deployment Deployment=Passed, SanityTest=unstable"
					ICON = "unstable.gif"
					STATUS = "UNSTABLE"
					env.deployment_result = "SUCCESS"
					env.sanity_result = "UNSTABLE"
					currentBuild.result = "UNSTABLE"
				} else if ( "${env.cortxCluster_status}" == "FAILURE" ) {
					manager.buildFailure()
					MESSAGE = "K8s Build#${build_id} 3node Deployment Deployment=failed, SanityTest=skipped"
					ICON = "error.gif"
					STATUS = "FAILURE"
					env.sanity_result = "FAILURE"
					env.deployment_result = "FAILURE"
					currentBuild.result = "FAILURE"
				} else {
					MESSAGE = "K8s Build#${build_id} 3node Deployment Deployment=unstable, SanityTest=unstable"
					ICON = "unstable.gif"
					STATUS = "UNSTABLE"
					env.sanity_result = "UNSTABLE"
					env.deployment_result = "UNSTABLE"
					currentBuild.result = "UNSTABLE"
				}
				env.build_setupcortx_url = sh( script: "echo ${env.cortxcluster_build_url}/artifact/artifacts/cortx-cluster-status.txt", returnStdout: true)
				env.host = "${env.allhost}"
				env.build_id = "${env.dockerimage_id}"
				env.build_location = "${DOCKER_IMAGE_LOCATION}"
				env.deployment_status = "${MESSAGE}"
				env.cluster_status = "${env.build_setupcortx_url}"
				if ( params.EMAIL_RECIPIENTS == "ALL" ) {
					mailRecipients = "cortx.sme@seagate.com, manoj.management.team@seagate.com, CORTX.SW.Architecture.Team@seagate.com, CORTX.DevOps.RE@seagate.com"
				} else if ( params.EMAIL_RECIPIENTS == "DEVOPS" ) {
					mailRecipients = "CORTX.DevOps.RE@seagate.com"
				} else if ( params.EMAIL_RECIPIENTS == "DEBUG" ) {
					mailRecipients = "shailesh.vaidya@seagate.com, abhijit.patil@seagate.com, amit.kapil@seagate.com"
				}
				emailext (
					body: '''${SCRIPT, template="K8s-deployment-email.template"}${SCRIPT, template="REL_QA_SANITY_CUS_EMAIL_3.template"}''',
					mimeType: 'text/html',
					subject: "${MESSAGE}",
					to: "${mailRecipients}",
					recipientProviders: [[$class: 'RequesterRecipientProvider']]
				)
				cleanWs()
			}
		}
	}
}
