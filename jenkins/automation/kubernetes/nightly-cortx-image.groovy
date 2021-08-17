pipeline {
    agent {
		node {
			label 'docker-centos-7.9.2009-node'
		}
	}

	options {
		timeout(time: 60, unit: 'MINUTES')
		timestamps()
	}
	

	parameters {  
        string(name: 'COMPONENT_BRANCH', defaultValue: 'kubernetes', description: 'Component Branch.')
	}	


    stages {
        stage ("Trigger custom-ci") {
            steps {
                script { build_stage = env.STAGE_NAME }
                build job: '../GitHub-custom-ci-builds/centos-7.9/custom-ci', wait: true,
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
                         	booleanParam(name: 'DOCKER_IMAGE',value: "true")
                        ]
            }   
        }
    }
}