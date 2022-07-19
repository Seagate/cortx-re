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
    // Please configure docker_host, vm_hosts and OSD_Disks parameter in Jenkins job configuration.
    }

    stages {
        stage ('Build Ceph Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {

                    try {
                        def buildCeph = build job: 'Ceph Build in Docker', wait: true,
                        parameters: [
                            string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                            string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}")
                        ]
                    }

                    catch(err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build Ceph Packages."
                    }
                }
            }
        }

        stage ('Build Ceph Image') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {

                    try {
                        def buildCephImage = build job: 'Ceph Build Container Image', wait: true
                    }

                    catch(err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build Ceph Image."
                    }
                }
            }
        }

        stage ('Deploy Ceph on hosts') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {

                    try {
                        def deployCephVM = build job: 'Ceph VM Deploy', wait: true,
                        parameters: [
                            string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                            string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                            text(name: 'hosts', value: "${vm_hosts}"),
                            text(name: 'hosts', value: "${OSD_Disks}")
                        ]
                    }

                    catch(err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Deploy Ceph on VM."
                    }
                }
            }
        }

        stage ('Deploy Ceph in Docker') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {

                    try {
                        def deployCephinDocker = build job: 'Ceph Docker Deploy', wait: true,
                        parameters: [
                            string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                            string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                            text(name: 'hosts', value: "${docker_hosts}"),
                            text(name: 'CEPH_IMAGE', value: "${CEPH_IMAGE}")
                        ]
                    }

                    catch(err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Deploy Ceph in Docker."
                    }
                }
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