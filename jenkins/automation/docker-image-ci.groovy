pipeline {
    agent {
        node {
           label "build-retention"
        }
    }
    
       environment {
        // GITHUB_CRED = credentials('shailesh-github')
        SERVICE_NAME = "${ENVIRONMENT == 'internal-ci' ? "cortx-build-internal-$OS_VERSION" : "cortx-build-$OS_VERSION"}"
        REPO_NAME = "${ENVIRONMENT == 'internal-ci' ? "ghcr.io/seagate/cortx-re" : "ghcr.io/seagate"}"
        REGISTRY = "ssc-vm-g4-rhev4-1774.colo.seagate.com"
        LOCAL_REG_CRED = credentials('dev-harbor')
    }


    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    parameters {

        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash to build docker image', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository to build docker image', trim: true)

        choice (
            name: 'OS_VERSION', 
            choices: ['rockylinux-8.4', 'centos-7.9.2009', 'centos-7.8.2003'],
            description: 'OS Version'
        )

        choice (
            name: 'ENVIRONMENT', 
            choices: ['internal-ci', 'opensource-ci'],
            description: 'CI Environment'
        )

        choice (
            choices: ['no' , 'yes'],
            description: 'Push newly built Docker image to GitHub ',
            name: 'GITHUB_PUSH'
        )

    }    

    stages {

        stage('Checkout Script') {
            steps {             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])                
                }
            }
        }

        stage ('Build Docker image') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build Docker image', script: '''
                echo -e "Running on $HOSTNAME with Operating System as $(cat /etc/redhat-release)"
                        #Clean Up
                        echo 'y' | docker image prune
                        if [ ! -z \$(docker ps -a -q) ]; then docker rm -f \$(docker ps -a -q); fi
                        mkdir -p /mnt/docker/tmp
                        docker-compose -f docker/cortx-build/docker-compose.yml build --force-rm --no-cache --compress --build-arg GIT_HASH="$(git rev-parse --short HEAD)" $SERVICE_NAME
                        echo 'y' | docker image prune
                        '''
               }
            }

        stage ('Validation') {
            when {
                expression { params.GITHUB_PUSH == 'yes' }
            }
            steps {
                script { build_stage = env.STAGE_NAME }
                               
                checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '/mnt/workspace/'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: false, recursiveSubmodules: true, reference: '', shallow: true, trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/Seagate/cortx', credentialsId: 'nitisdev-github-token']]]

                sh label: 'Validate Docker image', script: '''
                IMAGE_NAME=$(echo $SERVICE_NAME | sed 's/-c/:c/g')
                docker run --rm -v /mnt/workspace:/cortx-workspace -v /mnt/artifacts:/var/artifacts $REPO_NAME/$IMAGE_NAME make clean build
                echo "CORTX Packages generated..."
                grep -w "cortx-motr\\|cortx-s3server\\|cortx-hare\\|cortx-csm_agent\\|cortx-csm_web\\|cortx-sspl\\|cortx-s3server\\|cortx-prvsnr" /mnt/artifacts/0/cortx_iso/RELEASE.INFO
                cat /mnt/artifacts/0/cortx_iso/RELEASE.INFO
                rm -rf /mnt/artifacts/* /mnt/workspace/*
                '''
            }
        }

        stage ('Push Docker image') {
            when {
                expression { params.GITHUB_PUSH == 'yes' }
            }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build Docker image', script: '''
                docker login ghcr.io -u ${GITHUB_CRED_USR} -p ${GITHUB_CRED_PSW}
                if [ $ENVIRONMENT == "internal-ci" ]; then
                    docker-compose -f docker/cortx-build/docker-compose.yml push cortx-build-internal-$OS_VERSION
                else
                    docker-compose -f docker/cortx-build/docker-compose.yml push cortx-build-$OS_VERSION
                fi    
                '''
            }
        }

        stage ('Push to dev harbor') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Push to dev harbor', script: """
                docker tag cortx-docker.colo.seagate.com/seagate/cortx-build-internal:rockylinux-8.4 ssc-vm-g4-rhev4-1774.colo.seagate.com/cortx-build-ci/cortx-build-internal_rockylinux-8.4:build_${BUILD_NUMBER}
                docker tag cortx-docker.colo.seagate.com/seagate/cortx-build-internal:rockylinux-8.4 ssc-vm-g4-rhev4-1774.colo.seagate.com/cortx-build-ci/cortx-build-internal_rockylinux-8.4:latest
                docker push ssc-vm-g4-rhev4-1774.colo.seagate.com/cortx-build-ci/cortx-build-internal_rockylinux-8.4:build_${BUILD_NUMBER}
                docker push ssc-vm-g4-rhev4-1774.colo.seagate.com/cortx-build-ci/cortx-build-internal_rockylinux-8.4:latest

                echo "--------------Delete images--------------"
                docker rmi cortx-docker.colo.seagate.com/seagate/cortx-build-internal:rockylinux-8.4
                docker rmi ssc-vm-g4-rhev4-1774.colo.seagate.com/cortx-build-ci/cortx-build-internal_rockylinux-8.4:build_${BUILD_NUMBER}
                docker rmi ssc-vm-g4-rhev4-1774.colo.seagate.com/cortx-build-ci/cortx-build-internal_rockylinux-8.4:latest
                """
            }
        }
    }

    post {

        always {
            script {

                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                
                def mailRecipients = "nitish.singh@seagate.com"
                emailext ( 
                    body: '''${SCRIPT, template="release-email.template"}''',
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