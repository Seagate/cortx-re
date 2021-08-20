pipeline {
    agent {
		node {
			label 'docker-centos-7.9.2009-node'
		}
	}

    triggers { cron('30 19 * * *') }

	options {
		timeout(time: 60, unit: 'MINUTES')
		timestamps()
	}
	

	parameters {  
        string(name: 'COMPONENT_BRANCH', defaultValue: 'kubernetes', description: 'Component Branch.')

        choice (
            choices: ['ALL','DEVOPS'],
            description: 'Email Notification Recipients ',
            name: 'EMAIL_RECIPIENTS'
        )
	}	


    stages {
        stage ("Trigger custom-ci") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                def custom_ci = build job: '../GitHub-custom-ci-builds/centos-7.9/custom-ci', wait: true,
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
                            ]
                    env.custom_ci_build_id = custom_ci.rawBuild.id            
                }
            }   
        }

        stage ("Build cortx-all docker image") {
			steps {
				script { build_stage = env.STAGE_NAME }	
				script {
					try {	
						def docker_image_build = build job: 'cortx-all-docker-image', wait: true,
									parameters: [
										string(name: 'CORTX_RE_URL', value: "https://github.com/Seagate/cortx-re"),
										string(name: 'CORTX_RE_BRANCH', value: "kubernetes"),
										string(name: 'BUILD_URL', value: "http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/centos-7.9.2009/custom-build-${env.custom_ci_build_id}"),
                                        string(name: 'EMAIL_RECIPIENTS', value: "${EMAIL_RECIPIENTS}"),

									]
					} catch (err) {
						build_stage = env.STAGE_NAME
						error "Failed to Build Docker Image"
					}
				}
            }
        }	
    }
}