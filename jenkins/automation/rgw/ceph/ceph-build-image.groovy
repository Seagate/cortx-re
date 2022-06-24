pipeline {
    agent {
        node {
            label 'ceph-image-build'
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
    }

    parameters {
        string(name: 'CEPH_CONTAINER_REPO', defaultValue: 'https://github.com/nitisdev/ceph-container/', description: 'Repository for ceph container image scripts.', trim: true)
        string(name: 'CEPH_CONTAINER_BRANCH', defaultValue: 'centos-custom', description: 'Branch or GitHash for ceph container image scripts.', trim: true)
        string(name: 'CEPH_RELEASE', defaultValue: 'quincy', description: 'Ceph release to build image from.', trim: true)
        string(name: 'OS_IMAGE', defaultValue: 'centos', description: 'Base OS docker image to build from.', trim: true)
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
                    make FLAVORS=${CEPH_RELEASE},${OS_IMAGE},${OS_IMAGE_TAG} build

                    echo -e "==============================\n"

                    echo "List created images:"
                    docker images --format "{{.Repository}}:{{.Tag}}" --filter reference=ceph/daemon-base:HEAD-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}
                    docker images --format "{{.Repository}}:{{.Tag}}" --filter reference=ceph/daemon:HEAD-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}
                """
            }
        }

        stage ('Push Image to Registry') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Push Image to Registry', script: """

                    echo "Tag container images"
                    docker tag ceph/daemon:HEAD-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH} nitisdev/ceph:daemon-centos-custom-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}
                    docker tag ceph/daemon-base:HEAD-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH} nitisdev/ceph:daemon-base-centos-custom-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}

                    echo -e "==============================\n"

                    echo "Pushing images"
                    #Currently pushing to test DockerHub repository will migrate to internal Harbor pending versioning process.
                    docker push -a nitisdev/ceph

                    echo -e "==============================\n"

                    echo "To pull image from docker:"
                    echo "docker pull nitisdev/ceph:daemon-centos-custom-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}"

                    echo "Untag & remove local images"
                    docker rmi ceph/daemon-base:HEAD-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}
                    docker rmi ceph/daemon:HEAD-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}
                    docker rmi nitisdev/ceph:daemon-centos-custom-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}
                    docker rmi nitisdev/ceph:daemon-base-centos-custom-${CEPH_RELEASE}-${OS_IMAGE}-${OS_IMAGE_TAG}-${ARCH}
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