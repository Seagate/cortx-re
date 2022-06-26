pipeline {
    agent {
        node {
            label 'docker-k8-deployment-node'
        }
    }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
        disableConcurrentBuilds()   
    }

    environment {
        CEPH_DOCKER_DEPLOYMENT = "true"
    }

    parameters {
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts.', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts.', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VM details to be used. Currently only single node is supported for image deployment, but from ceph dashboard cluster can be expanded to multinode configuration.', name: 'hosts')
        string(name: 'CEPH_IMAGE', defaultValue: 'cortx-docker.colo.seagate.com/ceph/quincy-rockylinux_8:daemon-rockylinux-custom-quincy-rockylinux_8-x86_64-latest', description: 'Ceph docker image to deploy cluster from.', trim: true)
    }

    stages {
        stage('Checkout Script') {
            steps { 
                cleanWs()            
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])                
                }
            }
        }

        stage ('Deploy Ceph Prerequisites') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Install Ceph Prerequisites', script: """
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        export CEPH_IMAGE=${CEPH_IMAGE}
                        bash ceph-deploy.sh --prereq-ceph-docker
                    popd
                """
            }
        }

        stage ('Deploy Ceph') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Install Ceph Packages', script: """
                    pushd solutions/kubernetes/
                        export CEPH_IMAGE=${CEPH_IMAGE}
                        bash ceph-deploy.sh --deploy-ceph-docker
                    popd
                """
            }
        }

        stage ('IO Operation') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'IO Operation', script: """
                    pushd solutions/kubernetes/
                        export CEPH_DOCKER_DEPLOYMENT=${CEPH_DOCKER_DEPLOYMENT}
                        bash ceph-deploy.sh --io-operation
                    popd
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