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
        string(name: 'CORTX_RE_URL', defaultValue: 'https://github.com/Seagate/cortx-re.git', description: 'Repository URL for symas-openldap image build.')
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch for symas-openldap image build.')
        string(name: 'BUILD_URL', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx/github/main/centos-7.9.2009/last_successful_prod/', description: 'Build URL for symas-openldap docker image')

        choice (
            choices: ['yes' , 'no'],
            description: 'Push newly built Docker image to GitHub ',
            name: 'GITHUB_PUSH'
        )

        choice (
            choices: ['DEVOPS', 'ALL'],
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
    
        stage('Checkout') {
            steps {
                script { build_stage = env.STAGE_NAME }
                checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'PathRestriction', excludedRegions: '', includedRegions: 'scripts/third-party-rpm/.*']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_URL}"]]])
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

        stage('Build & push Image') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh encoding: 'utf-8', label: 'Build symas-openldap docker image', script: """
                    pushd ./docker/symas-openldap
                        if [ $GITHUB_PUSH == yes ];then
                            sh ./build.sh -b $BUILD_URL -t yes
                        else
                            sh ./build.sh -b $BUILD_URL -p no
                        fi
                    popd
                    docker logout  
                """
            }
        }
    }

    post {

        always {
            cleanWs()
            script {
                env.docker_image_location = "https://github.com/Seagate/cortx-re/pkgs/container/symas-openldap"
                env.image = sh( script: "docker images --format='{{.Repository}}:{{.Tag}}' | head -1", returnStdout: true).trim()
                env.build_stage = "${build_stage}"
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                if ( params.EMAIL_RECIPIENTS == "ALL" ) {
                    mailRecipients = "cortx.sme@seagate.com, manoj.management.team@seagate.com, CORTX.SW.Architecture.Team@seagate.com, CORTX.DevOps.RE@seagate.com"
                } else if ( params.EMAIL_RECIPIENTS == "DEVOPS" ) {
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