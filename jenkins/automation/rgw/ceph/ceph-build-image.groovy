pipeline {
    agent {
        node {
            label 'docker-image-builder-centos-7.9.2009'
        }
    }

    triggers { cron('30 19 * * *') }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
        disableConcurrentBuilds()   
    }

    environment {
        ARCH = "x86_64"
        CEPH_PROJECT = "ceph"
        REGISTRY = "cortx-docker.colo.seagate.com"
        LOCAL_REG_CRED = credentials('local-registry-access')
    }

    parameters {
        string(name: 'CEPH_CONTAINER_REPO', defaultValue: 'https://github.com/nitisdev/ceph-container/', description: 'Repository for ceph container image scripts.', trim: true)
        string(name: 'CEPH_CONTAINER_BRANCH', defaultValue: 'rockylinux-custom', description: 'Branch or GitHash for ceph container image scripts.', trim: true)
        string(name: 'CEPH_RELEASE', defaultValue: 'quincy', description: 'Ceph release to build image from.', trim: true)
        string(name: 'OS_IMAGE', defaultValue: 'rockylinux', description: 'Base OS docker image to build from.', trim: true)
        string(name: 'OS_IMAGE_TAG', defaultValue: '8', description: 'OS docker image tag.', trim: true)
    }    

    stages {
        stage('Checkout ceph-container repo') {
            steps { 
                cleanWs()            
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CEPH_CONTAINER_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CEPH_CONTAINER_REPO}"]]])                
                }
            }
        }

        stage ('Build Ceph Container Image') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build Ceph Container Image', script: """
                    echo "Starting Build"
                    make FLAVORS=${CEPH_RELEASE},${OS_IMAGE},${OS_IMAGE_TAG} RELEASE=${CEPH_CONTAINER_BRANCH} build

                    echo -e "==============================\n"

                    echo "List created images:"
                    docker images --format "{{.Repository}}:{{.Tag}}" --filter reference=${CEPH_PROJECT}/daemon-base:${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}
                    docker images --format "{{.Repository}}:{{.Tag}}" --filter reference=${CEPH_PROJECT}/daemon:${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}
                """
            }
        }

        stage ('Push Image to Registry') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Push Image to Registry', script: """

                    echo "Tag container images"
                    docker tag ${CEPH_PROJECT}/daemon:${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH} ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-build_${BUILD_NUMBER}
                    docker tag ${CEPH_PROJECT}/daemon-base:${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH} ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-base-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-build_${BUILD_NUMBER}
                    docker tag ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-build_${BUILD_NUMBER} ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-latest
                    docker tag ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-base-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-build_${BUILD_NUMBER} ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-base-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-latest

                    echo -e "==============================\n"

                    echo "Docker login"
                    docker login ${REGISTRY} -u ${LOCAL_REG_CRED_USR} -p ${LOCAL_REG_CRED_PSW}

                    echo "Pushing images"
                    docker push ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-build_${BUILD_NUMBER}
                    docker push ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-base-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-build_${BUILD_NUMBER}
                    docker push ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-latest
                    docker push ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-base-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-latest

                    echo -e "==============================\n"

                    echo "To pull image from docker:"
                    echo "docker pull ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-build_${BUILD_NUMBER}"

                    echo "Untag & remove local images"
                    docker rmi ${CEPH_PROJECT}/daemon-base:${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}
                    docker rmi ${CEPH_PROJECT}/daemon:${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}
                    docker rmi ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-build_${BUILD_NUMBER}
                    docker rmi ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-base-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-build_${BUILD_NUMBER}
                    docker rmi ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-latest
                    docker rmi ${REGISTRY}/${CEPH_PROJECT}/${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}:daemon-base-${CEPH_CONTAINER_BRANCH}-${CEPH_RELEASE}-${OS_IMAGE}_${OS_IMAGE_TAG}-${ARCH}-latest
                """
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}