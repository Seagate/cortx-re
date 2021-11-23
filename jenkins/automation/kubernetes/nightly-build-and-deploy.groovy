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
    }

    environment {
        CORTX_RE_BRANCH = "kubernetes"
        CORTX_RE_REPO = "https://github.com/Seagate/cortx-re/"
    }	

    parameters {
        string(name: 'COMPONENT_BRANCH', defaultValue: 'kubernetes', description: 'Component Branch.')
	choice (
            choices: ['DEVOPS'],
            description: 'Email Notification Recipients ',
            name: 'EMAIL_RECIPIENTS'
        )
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VM details to be used for K8 cluster setup. First node will be used as Master', name: 'hosts')
	string(name: 'CORTX_SCRIPTS_BRANCH', defaultValue: 'v0.0.14', description: 'Release for cortx-k8s scripts (Services Team)', trim: true)
	string(name: 'CORTX_SCRIPTS_REPO', defaultValue: 'Seagate/cortx-k8s', description: 'Repository for cortx-k8s scripts (Services Team)', trim: true)
    }
    stages {
	stage ("Build creation and k8s cluster setup jobs") {
		parallel {
			stage ("trigger custom-ci") {
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
			stage ("destroy-cluster") {
				steps {
					script { build_stage = env.STAGE_NAME }
					script {
						build job: '/Cortx-kubernetes/destroy-cluster', wait: true,
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
	stage ("build-cortx-all-docker-image") {
		steps {
			script { build_stage = env.STAGE_NAME }
			script {
				try {
					build job: '/Cortx-kubernetes/cortx-all-docker-image', wait: true,
					parameters: [
						string(name: 'CORTX_RE_URL', value: "${CORTX_RE_REPO}"),
						string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
						string(name: 'BUILD', value: "kubernetes-build-${env.custom_ci_build_id}"),
						string(name: 'EMAIL_RECIPIENTS', value: "${EMAIL_RECIPIENTS}"),
					]
				} catch (err) {
					build_stage = env.STAGE_NAME
					error "Failed to Build Docker Image"
				}
			}
		}
	}
	stage ("setup-cortx-cluster") {
		steps {
			script { build_stage = env.STAGE_NAME }
			script {
				CORTX_IMAGE="ghcr.io/seagate/cortx-all:2.0.0-${env.custom_ci_build_id}-custom-ci"
				build job: '/Cortx-kubernetes/setup-cortx-cluster', wait: true,
				parameters: [
					string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
					string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
					string(name: 'CORTX_IMAGE', value: "${CORTX_IMAGE}"),
					text(name: 'hosts', value: "${hosts}"),
					string(name: 'CORTX_SCRIPTS_BRANCH', value: "${CORTX_SCRIPTS_BRANCH}"),
					string(name: 'CORTX_SCRIPTS_REPO', value: "${CORTX_SCRIPTS_REPO}"),
				]
			}
		}
	}
}
}
