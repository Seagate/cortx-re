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
    }

    parameters {  
        string(name: 'BASE_IMAGE_NAME', defaultValue: 'ghcr.io/seagate/cortx-<component>:2.0.0-latest', description: 'Docker Image to be tagged.')
        string(name: 'TAGGED_IMAGE_NAME', defaultValue: 'ghcr.io/seagate/cortx-<component>:TAG', description: 'Tag to be used.')
    
        choice (
            choices: ['DEVOPS', 'DEBUG'],
            description: 'Email Notification Recipients ',
            name: 'EMAIL_RECIPIENTS'
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

        stage ('GHCR Login') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'GHCR Login', script: '''
                docker login ghcr.io -u ${GITHUB_CRED_USR} -p ${GITHUB_CRED_PSW}
                '''
            }
        }

        stage('Tag and Push Image') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh encoding: 'utf-8', label: 'Tag and push docker image', script: """
                    docker pull $BASE_IMAGE_NAME
                    docker tag $BASE_IMAGE_NAME $TAGGED_IMAGE_NAME
                    docker push $TAGGED_IMAGE_NAME
                    
                    docker logout  
                """
            }
        }
    }

    post {

        always {
            cleanWs()
            script {
                env.docker_image_location = "https://github.com/orgs/Seagate/packages?repo_name=cortx"
                env.image = sh( script: "docker images --format='{{.Repository}}:{{.Tag}}' | head -1", returnStdout: true).trim()
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