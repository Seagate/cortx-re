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

    parameters {
        string(name: 'CEPH_CONTAINER_REPO', defaultValue: 'https://github.com/nitisdev/ceph-container/', description: 'Repository for ceph container image scripts', trim: true)
        string(name: 'CEPH_CONTAINER_BRANCH', defaultValue: 'centos-custom', description: 'Branch or GitHash for ceph container image scripts', trim: true)
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
                sh label: 'Build Ceph Container Image', script: '''
                    echo "Starting Build"
                    make FLAVORS=quincy,centos,8 build

                    echo -e "==============================\n"

                    echo "List created images:"
                    docker images --format "{{.Repository}}:{{.Tag}}" --filter reference=ceph/daemon-base:centos-custom-quincy-centos-8-x86_64
                    docker images --format "{{.Repository}}:{{.Tag}}" --filter reference=ceph/daemon:centos-custom-quincy-centos-8-x86_64
                '''
            }
        }

        stage ('Push Image to Registry') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Push Image to Registry', script: '''

                    echo "Currently pushing to test DockerHub repository will migrate to internal Harbor pending versioning process."

                    docker tag ceph/daemon:centos-custom-quincy-centos-8-x86_64 nitisdev/ceph:daemon-centos-custom-quincy-centos-8-x86_64
                    docker tag ceph/daemon-base:centos-custom-quincy-centos-8-x86_64 nitisdev/ceph:daemon-base-centos-custom-quincy-centos-8-x86_64

                    echo -e "==============================\n"

                    echo "To pull image from docker:"
                    echo "docker pull nitisdev/ceph:daemon-centos-custom-quincy-centos-8-x86_64"

                '''
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}