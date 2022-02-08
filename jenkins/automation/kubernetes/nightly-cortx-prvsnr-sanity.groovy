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

    environment {
        CORTX_RE_BRANCH = "main"
        CORTX_RE_REPO = "https://github.com/Seagate/cortx-re/"
    }

    parameters {
        string(name: 'CORTX_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-all:2.0.0-latest-custom-ci', description: 'CORTX-ALL image', trim: true)
        string(name: 'SNS_CONFIG', defaultValue: '1+0+0', description: 'sns configuration for deployment. Please select value based on disks available on nodes.', trim: true)
        string(name: 'DIX_CONFIG', defaultValue: '1+0+0', description: 'dix configuration for deployment. Please select value based on disks available on nodes.', trim: true)
        // Please configure hosts, CORTX_SCRIPTS_BRANCH and CORTX_SCRIPTS_REPO parameter in Jenkins job configuration.
        // Please configure M_NODE, HOST_PASS  and EMAIL_RECEPIENTS parameter in Jenkins job configuration for prvsnr sanity test.
    }

    stages {
        stage ("Deploy CORTX-Prvsnr") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    catchError(stageResult: 'FAILURE') {
                        def cortxCluster = build job: '/Cortx-kubernetes/setup-cortx-cluster', wait: true,
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
                        env.cortxcluster_build_url = cortxCluster.absoluteUrl
                        env.cortxCluster_status = cortxCluster.currentResult
                    }
                }
            }
        }

        stage ("Prvsnr Sanity") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    catchError(stageResult: 'FAILURE') {
                        def prvsnrSanity = build job: '/Provisioner/Prvsnr-Sanity-Test', wait: true,
                        parameters: [
                            string(name: 'M_NODE', value: "${M_NODE}"),
                            string(name: 'HOST_PASS', value: "${HOST_PASS}"),
                            string(name: 'EMAIL_RECEPIENTS', value: "${EMAIL_RECEPIENTS}")
                        ]
                        env.prvsnrSanity_build_url = prvsnrSanity.absoluteUrl
                        env.prvsnrSanity_status = prvsnrSanity.currentResult
                    }
                    copyArtifacts filter: 'log/*result.xml', fingerprintArtifacts: true, flatten: true, optional: true, projectName: '/Provisioner/Prvsnr-Sanity-Test', selector: lastCompleted(), target: 'log/'
                    copyArtifacts filter: 'log/*result.html', fingerprintArtifacts: true, flatten: true, optional: true, projectName: '/Provisioner/Prvsnr-Sanity-Test', selector: lastCompleted(), target: 'log/'
                }
            }
        }
    }

    post {
        always {
            script {
                junit allowEmptyResults: true, testResults: '*result.xml'
                echo "${env.cortxCluster_status}"
                echo "${env.prvsnrSanity_status}"
                if ( "${env.cortxCluster_status}" == "SUCCESS" && "${env.prvsnrSanity_status}" == "SUCCESS" ) {
                    MESSAGE = "K8s Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=Passed, SanityTest=Passed"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                    env.deployment_result = "SUCCESS"
                    env.sanity_result = "SUCCESS"
                    currentBuild.result = "SUCCESS"
                } else if ( "${env.cortxCluster_status}" == "FAILURE" || "${env.cortxCluster_status}" == "UNSTABLE" || "${env.cortxCluster_status}" == "null" ) {
                    manager.buildFailure()
                    MESSAGE = "K8s Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=failed, SanityTest=skipped"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
                    env.sanity_result = "SKIPPED"
                    env.deployment_result = "FAILURE"
                    currentBuild.result = "FAILURE"
                } else if ( "${env.cortxCluster_status}" == "SUCCESS" && "${env.prvsnrSanity_status}" == "FAILURE" || "${env.prvsnrSanity_status}" == "null" ) {
                    manager.buildFailure()
                    MESSAGE = "K8s Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=Passed, SanityTest=failed"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
                    env.sanity_result = "FAILURE"
                    env.deployment_result = "SUCCESS"
                    currentBuild.result = "FAILURE"
                } else if ( "${env.cortxCluster_status}" == "SUCCESS" && "${env.prvsnrSanity_status}" == "UNSTABLE" ) {
                    MESSAGE = "K8s Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=Passed, SanityTest=passed"
                    ICON = "unstable.gif"
                    STATUS = "UNSTABLE"
                    env.deployment_result = "SUCCESS"
                    env.sanity_result = "UNSTABLE"
                    currentBuild.result = "UNSTABLE"
                } else {
                    MESSAGE = "K8s Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=unstable, SanityTest=unstable"
                    ICON = "unstable.gif"
                    STATUS = "UNSTABLE"
                    env.sanity_result = "UNSTABLE"
                    env.deployment_result = "UNSTABLE"
                    currentBuild.result = "UNSTABLE"
                }
                mailRecipients = "${EMAIL_RECEPIENTS}"
                catchError(stageResult: 'FAILURE') {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'log/*result.xml, log/*result.html, support_bundle/*.tar, crash_files/*.gz', followSymlinks: false
                    emailext (
                        body: '''${SCRIPT, template="K8s-deployment-email_2.template"}${SCRIPT, template="REL_QA_SANITY_CUS_EMAIL_RETEAM_5.template"}''',
                        mimeType: 'text/html',
                        subject: "${MESSAGE}",
                        to: "${mailRecipients}",
                        recipientProviders: [[$class: 'RequesterRecipientProvider']]
                    )
                }
                cleanWs()
            }
        }
    }
}