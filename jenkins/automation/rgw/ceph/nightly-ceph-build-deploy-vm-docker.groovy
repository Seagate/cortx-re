pipeline {
    agent {
        node {
            label 'docker-centos-7.9.2009-node'
        }
    }

    triggers { cron('30 19 * * *') }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    parameters {
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts.', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts.', trim: true)
        string(name: 'CEPH_REPO', defaultValue: 'https://github.com/ceph/ceph', description: 'Repository for Cluster Setup scripts.', trim: true)
        string(name: 'CEPH_BRANCH', defaultValue: 'quincy', description: 'Branch or GitHash for Cluster Setup scripts.', trim: true)
        string(name: 'CEPH_IMAGE', defaultValue: 'cortx-docker.colo.seagate.com/ceph/quincy-rockylinux_8:daemon-rockylinux-custom-quincy-rockylinux_8-x86_64-latest', description: 'Ceph docker image to deploy cluster from.', trim: true)
        string(name: 'CEPH_CONTAINER_REPO', defaultValue: 'https://github.com/nitisdev/ceph-container/', description: 'Repository for ceph container image scripts.', trim: true)
        string(name: 'CEPH_CONTAINER_BRANCH', defaultValue: 'rockylinux-custom', description: 'Branch or GitHash for ceph container image scripts.', trim: true)
        string(name: 'CEPH_RELEASE', defaultValue: 'quincy', description: 'Ceph release to build image from.', trim: true)
        string(name: 'OS_IMAGE', defaultValue: 'rockylinux', description: 'Base OS docker image to build from.', trim: true)
        string(name: 'OS_IMAGE_TAG', defaultValue: '8', description: 'OS docker image tag.', trim: true)
    // Please configure docker_hosts, vm_hosts and OSD_Disks parameter in Jenkins job configuration.
    }

    stages {
        stage ('Build Ceph Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    catchError(stageResult: 'FAILURE') {
                        build job: 'Ceph Build in Docker', wait: true,
                        parameters: [
                            string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                            string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                            string(name: 'CEPH_BRANCH', value: "${CEPH_BRANCH}"),
                            string(name: 'CEPH_REPO', value: "${CEPH_REPO}")
                        ]
                    }
                }
            }
        }

        stage ('Build Ceph Image') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    catchError(stageResult: 'FAILURE') {
                        build job: 'Ceph Build Container Image', wait: true,
                        parameters: [
                            string(name: 'CEPH_CONTAINER_REPO', value: "${CEPH_CONTAINER_REPO}"),
                            string(name: 'CEPH_CONTAINER_BRANCH', value: "${CEPH_CONTAINER_BRANCH}"),
                            string(name: 'CEPH_RELEASE', value: "${CEPH_RELEASE}"),
                            string(name: 'OS_IMAGE', value: "${OS_IMAGE}"),
                            string(name: 'OS_IMAGE_TAG', value: "${OS_IMAGE_TAG}")
                        ]
                    }
                }
            }
        }

        stage ('Destroy Ceph on VM') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    catchError(stageResult: 'FAILURE') {
                        build job: 'Ceph Destroy', wait: true,
                        parameters: [
                            string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                            string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                            string(name: 'DEPLOYMENT_TYPE', value: "VM_DEPLOYMENT"),
                            text(name: 'hosts', value: "${vm_hosts}")
                        ]
                    }                    
                }
            }
        }

        stage ('Destroy Ceph on Docker') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    catchError(stageResult: 'FAILURE') {
                        build job: 'Ceph Destroy', wait: true,
                        parameters: [
                            string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                            string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                            string(name: 'DEPLOYMENT_TYPE', value: "DOCKER_DEPLOYMENT"),
                            text(name: 'hosts', value: "${docker_hosts}")
                        ]
                    }
                }
            }
        }

        stage ('Deploy Ceph on hosts') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    catchError(stageResult: 'FAILURE') {
                        build job: 'Ceph VM Deploy', wait: true,
                        parameters: [
                            string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                            string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                            text(name: 'hosts', value: "${vm_hosts}"),
                            text(name: 'OSD_Disks', value: "${OSD_Disks}")
                        ]
                    }
                }
            }
        }

        stage ('Deploy Ceph in Docker') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    catchError(stageResult: 'FAILURE') {
                        build job: 'Ceph Docker Deploy', wait: true,
                        parameters: [
                            string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                            string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                            text(name: 'hosts', value: "${docker_hosts}"),
                            text(name: 'CEPH_IMAGE', value: "${CEPH_IMAGE}")
                        ]
                    }
                }
            }
        }
    }

    post {
        always {
            cleanWs()
            script {
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                mailRecipients = "CORTX.DevOps.RE@seagate.com"
                emailext ( 
                    body: '''${SCRIPT, template="cluster-setup-email.template"}''',
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