pipeline {
    agent {
        node {
            label 'prvsnr_sanity_g4-rhev4-0658'
        }
    }

    triggers { cron('30 19 * * *') }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '20', numToKeepStr: '20'))
    }

    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'prvsnr_sanity', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/nitsdev/cortx-re/', description: 'Repository for Cluster Setup scripts', trim: true)
        // Please configure CORTX_IMAGE, hosts, SNS_CONFIG, DIX_CONFIG, CORTX_SCRIPTS_BRANCH and CORTX_SCRIPTS_REPO parameter in Jenkins job configuration.
        // Please configure M_NODE, HOST_PASS  and EMAIL_RECEPIENTS parameter in Jenkins job configuration for prvsnr sanity test.

    }

    stages {
        stage ("Deploy CORTX-Prvsnr") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    catchError(stageResult: 'FAILURE') {
                        build job: '/Cortx-kubernetes/setup-cortx-cluster', wait: true,
                        parameters: [
                            string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                            string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                            string(name: 'CORTX_IMAGE', value: "${CORTX_IMAGE}"),
                            text(name: 'hosts', value: "${hosts}"),
                            string(name: 'SNS_CONFIG', value: "${SNS_CONFIG}"),
                            string(name: 'DIX_CONFIG', value: "${DIX_CONFIG}"),
                            string(name: 'CORTX_SCRIPTS_BRANCH', value: "${CORTX_SCRIPTS_BRANCH}"),
                            string(name: 'CORTX_SCRIPTS_REPO', value: "${CORTX_SCRIPTS_REPO}")
                        ]
                    }
                }
            }
        }

        stage ("Prvsnr Sanity") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    catchError(stageResult: 'FAILURE') {
                        build job: '/Provisioner/Prvsnr-Sanity-Test', wait: true,
                        parameters: [
                            string(name: 'M_NODE', value: "${M_NODE}"),
                            string(name: 'HOST_PASS', value: "${HOST_PASS}"),
                            string(name: 'EMAIL_RECEPIENTS', value: "${EMAIL_RECEPIENTS}")
                        ]
                    }
                }
            }
        }
    }

    post {
        always {
            script {
                catchError(stageResult: 'FAILURE') {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'log/*result.xml, log/*result.html, support_bundle/*.tar, crash_files/*.gz', followSymlinks: false
                }
                cleanWs()
            }
        }
    }
}