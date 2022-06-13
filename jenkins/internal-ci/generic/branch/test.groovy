#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
           label 'docker-image-builder-centos-7.9.2009'
        }
    }
    
    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '20'))  
    }

    environment {
        GITHUB_CRED = credentials('shailesh-github')
        LOCAL_REG_CRED = credentials('local-registry-access')
    }

    parameters {  
        string(name: 'CORTX_RE_URL', defaultValue: 'https://github.com/Seagate/cortx-re.git', description: 'Repository URL for cortx images build.')
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch for cortx images build.')
        string(name: 'BUILD', defaultValue: 'last_successful_prod', description: 'Build for cortx docker images')
        
        choice (
            choices: ['centos-7.9.2009' , 'rockylinux-8.4'],
            description: 'Base Operating System ',
            name: 'OS'
        )

        choice (
            choices: ['all', 'cortx-all', 'cortx-rgw', 'cortx-data', 'cortx-control'],
            description: 'CORTX Image to be built. Defaults to all images ',
            name: 'CORTX_IMAGE'
        )

        choice (
            choices: ['yes' , 'no'],
            description: 'Push newly built Docker image to GitHub ',
            name: 'GITHUB_PUSH'
        )

        choice (
            choices: ['yes' , 'no'],
            description: 'Tag newly Docker image as latest ',
            name: 'TAG_LATEST'
        )

        choice (
            choices: ['cortx-docker.colo.seagate.com' , 'ghcr.io'],
            description: 'Docker Registry to be used',
            name: 'DOCKER_REGISTRY'
        )

        choice (
            choices: ['DEVOPS', 'ALL', 'DEBUG'],
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
                   if docker images --format "{{.Repository}}:{{.Tag}}"| grep -E '*cortx-.*:2.0.0*' -q; then docker rmi --force \$(docker images --filter=reference='*/*/cortx*:[0-9]*' --filter=reference='*cortx*:[0-9]*' -q); fi
                """
            }
        }
    
        stage('Checkout') {
            steps {
                script { build_stage = env.STAGE_NAME }
                checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'PathRestriction', excludedRegions: '', includedRegions: 'scripts/third-party-rpm/.*']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_URL}"]]])
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

        stage('Build & push Image') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh encoding: 'utf-8', label: 'Build cortx docker images', script: """
                    pushd ./docker/cortx-deploy
                        if [ $GITHUB_PUSH == yes ] && [ $TAG_LATEST == yes ];then
                                sh ./build.sh -b $BUILD -p yes -t yes -r $DOCKER_REGISTRY -e internal-ci -o $OS -s $CORTX_IMAGE
                        elif [ $GITHUB_PUSH == yes ] && [ $TAG_LATEST == no ]; then
                                sh ./build.sh -b $BUILD -p yes -t no -r $DOCKER_REGISTRY -e internal-ci -o $OS -s $CORTX_IMAGE
                        else
                                sh ./build.sh -b $BUILD -p no -e internal-ci -o $OS -s $CORTX_IMAGE
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

                env.image = sh( script: "docker images --format='{{.Repository}}:{{.Tag}}' --filter=reference='*/*/cortx*:[0-9]*' | grep -v 2.0.0-latest | head -4", returnStdout: true).trim()
                println "${env.image}"

                env.cortx_all_image = sh( script: "docker images --format='{{.Repository}}:{{.Tag}}' --filter=reference='*/*/cortx-all:[0-9]*' | grep -v 2.0.0-latest", returnStdout: true).trim()
                env.cortx_rgw_image = sh( script: "docker images --format='{{.Repository}}:{{.Tag}}' --filter=reference='*/*/cortx-rgw:[0-9]*' | grep -v 2.0.0-latest", returnStdout: true).trim()
                env.cortx_data_image = sh( script: "docker images --format='{{.Repository}}:{{.Tag}}' --filter=reference='*/*/cortx-data:[0-9]*' | grep -v 2.0.0-latest", returnStdout: true).trim()
                env.cortx_control_image = sh( script: "docker images --format='{{.Repository}}:{{.Tag}}' --filter=reference='*/*/cortx-control:[0-9]*' | grep -v 2.0.0-latest", returnStdout: true).trim()

                env.build_stage = "${build_stage}"
                
                if ( params.DOCKER_REGISTRY == "ghcr.io" ) {
                    env.docker_image_location = "https://github.com/orgs/Seagate/packages?repo_name=cortx"
                } else if ( params.DOCKER_REGISTRY == "cortx-docker.colo.seagate.com" ) {
                    env.docker_image_location = "http://cortx-docker.colo.seagate.com/harbor/projects/2/repositories"
                }    

                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                if ( params.EMAIL_RECIPIENTS == "ALL" ) {
                    mailRecipients = "cortx.sme@seagate.com, manoj.management.team@seagate.com, CORTX.SW.Architecture.Team@seagate.com, CORTX.DevOps.RE@seagate.com"
                } else if ( params.EMAIL_RECIPIENTS == "DEVOPS" ) {
                    mailRecipients = "CORTX.DevOps.RE@seagate.com"
                } else if ( params.EMAIL_RECIPIENTS == "DEBUG" ) {
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