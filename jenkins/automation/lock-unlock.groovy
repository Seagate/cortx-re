pipeline {
	agent {
        node {
           label "automation-node-centos"
           // label "cortx-test-ssc-vm-4090"
        }
    }
    parameters {
        choice(
            name: 'flag',
            choices: ['Lock', 'Unlock'],
            description: 'Branch lock/unlock option'
        )
        extendedChoice(
            defaultValue: 'main',
            description: 'Branch name',
            multiSelectDelimiter: ',',
            name: 'branch',
            quoteValue: false,
            saveJSONParameterToFile: false,
            type: 'PT_CHECKBOX',
            value:'stable,main,cortx-1.0,kubernetes',
            visibleItemCount: 4
        )
        extendedChoice(
            defaultValue: 'cortx-re',
            description: 'Repo name',
            multiSelectDelimiter: ',',
            name: 'repo',
            quoteValue: false,
            saveJSONParameterToFile: false,
            type: 'PT_CHECKBOX',
            value:'cortx-re,cortx-manager,cortx-management-portal,cortx-ha,cortx-hare,cortx-motr,cortx-prvsnr,cortx-monitor,cortx-s3server,cortx-utils',
            visibleItemCount: 10
        )

    }
    environment {
        GIT_CRED = credentials('cortx-admin-github')
    }

    stages {
        stage('Checkout source code') {
                steps {
                    script {
                        checkout([$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/seagate/cortx-re/']]])
                    }
                }
        }

        stage('Running python script') {
            steps {
                echo "Executing the script..."
                echo "Branchs: ${branch}, Repos: ${repo}"
                sh "python3 ./scripts/automation/lock-unlock.py -f ${flag} -r ${repo} -b ${branch} -t ${GIT_CRED_PSW}"
            }
        }

	}

}

