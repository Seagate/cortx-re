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
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '20'))
    }

    environment {
        GITHUB_CRED = credentials('shailesh-github')
        LOCAL_REG_CRED = credentials('local-registry-access')
    }

    parameters {
        string(name: 'BASE_TAG', defaultValue: '2.0.0-latest', description: 'Release version to be tagged.')
        string(name: 'TARGET_TAG', defaultValue: '2.0.0-latest', description: 'Tag to be used.')

        choice (
            choices: ['DEVOPS', 'DEBUG'],
            description: 'Email Notification Recipients ',
            name: 'EMAIL_RECIPIENTS'
        )

        choice (
            choices: ['cortx-docker.colo.seagate.com' , 'ghcr.io'],
            description: 'Docker Registry to be used',
            name: 'DOCKER_REGISTRY'
        )
    }

    stages {

        stage('Prerequisite') {
            steps {
                sh encoding: 'utf-8', label: 'Validate Docker pre-requisite', script: """
                   systemctl status docker
                   /usr/local/bin/docker-compose --version
                   echo 'y' | docker image prune
                """
            }
        }

        stage ('Docker Registry Login') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Docker Registry Login', script: '''
                if [ "$DOCKER_REGISTRY" == "ghcr.io" ]; then
                    docker login ghcr.io -u ${GITHUB_CRED_USR} -p ${GITHUB_CRED_PSW}
                elif [ "$DOCKER_REGISTRY" == "cortx-docker.colo.seagate.com" ]; then
                    docker login cortx-docker.colo.seagate.com -u ${LOCAL_REG_CRED_USR} -p ${LOCAL_REG_CRED_PSW}
                fi
                '''
            }
        }

        stage('Tag and Push Image') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: '', script: '''
                for image in cortx-rgw cortx-control cortx-data
                do
                    echo -e "\\n Pulling $DOCKER_REGISTRY/seagate/$image:$BASE_TAG \\n"
                    docker pull $DOCKER_REGISTRY/seagate/$image:$BASE_TAG
                    echo -e "\\n Tagging $DOCKER_REGISTRY/seagate/$image:$BASE_TAG as $DOCKER_REGISTRY/seagate/$image:$TARGET_TAG"
                    docker tag $DOCKER_REGISTRY/seagate/$image:$BASE_TAG $DOCKER_REGISTRY/seagate/$image:$TARGET_TAG
                    docker push $DOCKER_REGISTRY/seagate/$image:$TARGET_TAG
                done
                    docker logout 
                '''
            }
        }
    }

    post {

        always {
            cleanWs()
            script {
                if ( params.DOCKER_REGISTRY == "ghcr.io" ) {
                    env.docker_image_location = "https://github.com/orgs/Seagate/packages?repo_name=cortx"
                } else if ( params.DOCKER_REGISTRY == "cortx-docker.colo.seagate.com" ) {
                    env.docker_image_location = "http://cortx-docker.colo.seagate.com/harbor/projects/2/repositories"
                }  

                 env.image = sh( script: "docker images --format='{{.Repository}}:{{.Tag}}' --filter=reference='*/*/cortx*:[0-9]*' | grep -v -E '2.0.0-latest|pr|custom-ci | head -4'", returnStdout: true).trim()

                env.cortx_rgw_image = sh( script: "docker images --format='{{.Repository}}:{{.Tag}}' --filter=reference='*/*/cortx-rgw:[0-9]*' | grep -v -E '2.0.0-latest|pr|custom-ci'", returnStdout: true).trim()
                env.cortx_data_image = sh( script: "docker images --format='{{.Repository}}:{{.Tag}}' --filter=reference='*/*/cortx-data:[0-9]*' | grep -v -E '2.0.0-latest|pr|custom-ci'", returnStdout: true).trim()
                env.cortx_control_image = sh( script: "docker images --format='{{.Repository}}:{{.Tag}}' --filter=reference='*/*/cortx-control:[0-9]*' | grep -v -E '2.0.0-latest|pr|custom-ci'", returnStdout: true).trim()

                env.build_stage = "${build_stage}"

                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                if ( params.EMAIL_RECIPIENTS == "DEVOPS" ) {
                    mailRecipients = "CORTX.DevOps.RE@seagate.com"
                } else {
                    mailRecipients = "shailesh.vaidya@seagate.com"
                }

                emailext (
                    body: '''${SCRIPT, template="docker-image-email.template"}''',
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