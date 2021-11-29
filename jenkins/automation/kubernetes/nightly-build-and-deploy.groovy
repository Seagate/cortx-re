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

    environment {
        CORTX_RE_BRANCH = "kubernetes"
        CORTX_RE_REPO = "https://github.com/Seagate/cortx-re/"
	DOCKER_IMAGE_LOCATION = "https://github.com/Seagate/cortx-re/pkgs/container/cortx-all"
    }	
    triggers { cron('30 19 * * *') }
    parameters {
        string(name: 'COMPONENT_BRANCH', defaultValue: 'kubernetes', description: 'Component Branch.')
	choice (
            choices: ['ALL', 'DEVOPS'],
            description: 'Email Notification Recipients ',
            name: 'EMAIL_RECIPIENTS'
        )
	string(name: 'CORTX_SCRIPTS_BRANCH', defaultValue: 'v0.0.14', description: 'Release for cortx-k8s scripts (Services Team)', trim: true)
	string(name: 'CORTX_SCRIPTS_REPO', defaultValue: 'Seagate/cortx-k8s', description: 'Repository for cortx-k8s scripts (Services Team)', trim: true)
    }
    // Please configure hosts parameter in Jenkins job configuration.
    stages {
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
				build job: '/Cortx-kubernetes/setup-cortx-cluster', wait: true,
				parameters: [
					string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
					string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
					string(name: 'CORTX_IMAGE', value: "${env.dockerimage_id}"),
					text(name: 'hosts', value: "${hosts}"),
					string(name: 'CORTX_SCRIPTS_BRANCH', value: "${CORTX_SCRIPTS_BRANCH}"),
					string(name: 'CORTX_SCRIPTS_REPO', value: "${CORTX_SCRIPTS_REPO}"),
				]
				env.cortxcluster_build_url = cortxCluster.buildVariables.build_setupcortx_url
			}
		}
	}
}
	post { 
		always {
			script {
				if ( currentBuild.currentResult == "SUCCESS" ) {
					MESSAGE = "#${build_id} 3node K8S Deployment Deployment=Passed, SanityTest=unstable"
					ICON = "accept.gif"
					STATUS = "UNSTABLE"
					env.sanity_result = "UNSTABLE"
				} else if ( currentBuild.currentResult == "FAILURE" ) {
			                manager.buildFailure()
			                MESSAGE = "#${build_id} 3node K8S Deployment Deployment=failed, SanityTest=failed"
			                ICON = "error.gif"
			                STATUS = "FAILURE"
					env.sanity_result = "FAILURE"
			        } else {
			                manager.buildUnstable()
			                MESSAGE = "#${build_id} 3node K8S Deployment Deployment=unstable, SanityTest=unstable"
					ICON = "warning.gif"
			                STATUS = "UNSTABLE"
					env.sanity_result = "UNSTABLE"
		                }
				sh '''	echo $hosts > postactionhost1.txt
					cat postactionhost1.txt
				'''
				env.allhost = sh( script: "cat postactionhost1.txt|tr ',' '\n'|grep hostname|rev|cut -d '=' -f1|rev|tr ' ' '\n'", returnStdout: true)
				env.host = "${env.allhost}"
                		env.build_id = "${env.dockerimage_id}"
		                env.build_location = "${DOCKER_IMAGE_LOCATION}"
		                env.deployment_status = "${MESSAGE}"
				env.cluster_status = "${env.cortxcluster_build_url}"
                		mailRecipients = "shailesh.vaidya@seagate.com, abhijit.patil@seagate.com"

		                emailext (
                			body: '''${SCRIPT, template="vm-deployment-email.template"}''',
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
