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
    }

    environment {
        CEPH_DOCKER_DEPLOYMENT = "true"
    }

    parameters {
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts.', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts.', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VM details to be used. Currently only single node is supported for image deployment.', name: 'hosts')
        string(name: 'CEPH_IMAGE', defaultValue: 'cortx-docker.colo.seagate.com/ceph/quincy-rockylinux_8:daemon-rockylinux-custom-quincy-rockylinux_8-x86_64-latest', description: 'Ceph docker image to deploy cluster from.', trim: true)
    }

    stages {
        stage ('Checkout Script') {
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
                sh label: 'Install Ceph Prerequisites', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        export CEPH_IMAGE=${CEPH_IMAGE}
                        bash ceph-deploy.sh --prereq-ceph-docker
                    popd
                '''
            }
        }

        stage ('Deploy Ceph') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Install Ceph Packages', script: '''
                    pushd solutions/kubernetes/
                        export CEPH_IMAGE=${CEPH_IMAGE}
                        bash ceph-deploy.sh --deploy-ceph-docker
                    popd
                '''
            }
        }

/*
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
*/

        stage ('Post Deployment') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Post Deployment', script: """
                    echo -e "########################################################"
                    echo -e "For IO Sanity please follow steps on the following page:"
                    echo -e "IO Sanity: https://seagate-systems.atlassian.net/wiki/spaces/PRIVATECOR/pages/1097859073/Ceph+Docker+Deployment+IO+Sanity"
                """
            }
        }
    }

    post {
        always {
            cleanWs()
            script {
                env.build_stage = "${build_stage}"

                def toEmail = ""
                def recipientProvidersClass = [[$class: 'DevelopersRecipientProvider']]
                if ( manager.build.result.toString() == "FAILURE" ) {
                    toEmail = "CORTX.DevOps.RE@seagate.com"
                    recipientProvidersClass = [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
                }

                emailext (
                    body: '''${SCRIPT, template="cluster-setup-email.template"}''',
                    mimeType: 'text/html',
                    subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                    attachLog: true,
                    to: toEmail,
                    recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}