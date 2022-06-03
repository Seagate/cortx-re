pipeline {
    agent {
        node {
            label 'docker-centos-7.9.2009-node'
        }
    }
    triggers { cron('30 19 * * *') }
    options {
        timeout(time: 600, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '20', numToKeepStr: '20'))
        disableConcurrentBuilds()
    }
    environment {
        CORTX_RE_BRANCH = "main"
        CORTX_RE_REPO = "https://github.com/Seagate/cortx-re"
        LOCAL_REG_CRED = credentials('local-registry-access')
        GITHUB_CRED = credentials('shailesh-github')
        VERSION = "2.0.0"
        last_success_build_number = getBuild(JOB_URL)
    }
    parameters {
        string(name: 'CORTX_SERVER_IMAGE', defaultValue: 'cortx-docker.colo.seagate.com/seagate/cortx-rgw:2.0.0-latest', description: 'CORTX-SERVER image', trim: true)
        string(name: 'CORTX_DATA_IMAGE', defaultValue: 'cortx-docker.colo.seagate.com/seagate/cortx-data:2.0.0-latest', description: 'CORTX-DATA image', trim: true)
        string(name: 'CORTX_CONTROL_IMAGE', defaultValue: 'cortx-docker.colo.seagate.com/seagate/cortx-control:2.0.0-latest', description: 'CORTX-CONTROL image', trim: true)
        choice (
            choices: ['ALL', 'DEVOPS', 'DEBUG'],
            description: 'Email Notification Recipients ',
            name: 'EMAIL_RECIPIENTS'
        )
    }
    // Please configure hosts,SNS, CORTX_SCRIPTS_REPO, CORTX_SCRIPTS_BRANCH and DIX parameter in Jenkins job configuration.
    stages {
        stage ("Define Variable") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    env.allhost = sh( script: '''
                    echo $hosts | tr ' ' '\n' | awk -F["="] '{print $2}'|cut -d',' -f1
                    ''', returnStdout: true).trim()
                
                    env.master_node = sh( script: '''
                    echo $hosts | tr ' ' '\n' | head -1 | awk -F["="] '{print $2}' | cut -d',' -f1
                    ''', returnStdout: true).trim()

                    env.hostpasswd = sh( script: '''
                    echo $hosts | tr ' ' '\n' | head -1 | awk -F["="] '{print $4}'
                    ''', returnStdout: true).trim()

                    env.numberofnodes = sh( script: '''
                    echo $hosts | tr ' ' '\n' | tail -n +2 | wc -l
                    ''', returnStdout: true).trim()

                }
            }
        }
        
        stage ("Deploy CORTX Cluster") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def cortxCluster = build job: '/Cortx-Automation/RGW/setup-cortx-rgw-cluster', wait: true,
                    parameters: [
                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                        string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                        string(name: 'CORTX_SERVER_IMAGE', value: "${CORTX_SERVER_IMAGE}"),
                        string(name: 'CORTX_DATA_IMAGE', value: "${CORTX_DATA_IMAGE}"),
                        string(name: 'CORTX_CONTROL_IMAGE', value: "${CORTX_CONTROL_IMAGE}"),
                        string(name: 'DEPLOYMENT_METHOD', value: "standard"),
                        text(name: 'hosts', value: "${hosts}"),
                        string(name: 'CORTX_SCRIPTS_BRANCH', value: "${CORTX_SCRIPTS_BRANCH}"),
                        string(name: 'CORTX_SCRIPTS_REPO', value: "${CORTX_SCRIPTS_REPO}"),
                        string(name: 'EXTERNAL_EXPOSURE_SERVICE', value: "NodePort"),
                        string(name: 'SNS_CONFIG', value: "${SNS_CONFIG}"),
                        string(name: 'DIX_CONFIG', value: "${DIX_CONFIG}")
                    ]
                    env.cortxcluster_build_url = cortxCluster.absoluteUrl
                    env.cortxCluster_status = cortxCluster.currentResult
                }
            }
        }

        stage('Push Image to GitHub') {
            agent {
                node { label 'docker-image-builder-centos-7.9.2009' }
            }
           steps {
                sh label: 'Push Image to GitHub', script: '''                   
                   systemctl status docker
                   /usr/local/bin/docker-compose --version
                   echo \'y\' | docker image prune
                   docker pull $CORTX_SERVER_IMAGE
                   docker pull $CORTX_DATA_IMAGE
                   docker pull $CORTX_CONTROL_IMAGE

                   echo "\n RPM Build URL used for Nightly Image"
                   docker inspect $CORTX_SERVER_IMAGE | jq -r '.[] | (.ContainerConfig.Cmd)' | grep 'BUILD_URL='
                   docker inspect $CORTX_DATA_IMAGE | jq -r '.[] | (.ContainerConfig.Cmd)' | grep 'BUILD_URL='
                   docker inspect $CORTX_CONTROL_IMAGE | jq -r '.[] | (.ContainerConfig.Cmd)' | grep 'BUILD_URL='
                   #Update VERSION details in RELEASE.INFO file

                   docker commit $(docker run -d ${CORTX_SERVER_IMAGE} sed -i /VERSION/s/\\"2.0.0.*\\"/\\"${VERSION}-${BUILD_NUMBER}\\"/ /opt/seagate/cortx/RELEASE.INFO) ghcr.io/seagate/cortx-rgw:${VERSION}-${BUILD_NUMBER}
                   docker commit $(docker run -d ${CORTX_DATA_IMAGE} sed -i /VERSION/s/\\"2.0.0.*\\"/\\"${VERSION}-${BUILD_NUMBER}\\"/ /opt/seagate/cortx/RELEASE.INFO) ghcr.io/seagate/cortx-data:${VERSION}-${BUILD_NUMBER}
                   docker commit $(docker run -d ${CORTX_CONTROL_IMAGE} sed -i /VERSION/s/\\"2.0.0.*\\"/\\"${VERSION}-${BUILD_NUMBER}\\"/ /opt/seagate/cortx/RELEASE.INFO) ghcr.io/seagate/cortx-control:${VERSION}-${BUILD_NUMBER} 

                   docker tag ghcr.io/seagate/cortx-rgw:${VERSION}-${BUILD_NUMBER} ghcr.io/seagate/cortx-rgw:${VERSION}-latest
                   docker tag ghcr.io/seagate/cortx-data:${VERSION}-${BUILD_NUMBER} ghcr.io/seagate/cortx-data:${VERSION}-latest
                   docker tag ghcr.io/seagate/cortx-control:${VERSION}-${BUILD_NUMBER} ghcr.io/seagate/cortx-control:${VERSION}-latest

                   docker login ghcr.io -u ${GITHUB_CRED_USR} -p ${GITHUB_CRED_PSW}

                   docker push ghcr.io/seagate/cortx-rgw:${VERSION}-${BUILD_NUMBER} && docker push ghcr.io/seagate/cortx-rgw:${VERSION}-latest
                   docker push ghcr.io/seagate/cortx-data:${VERSION}-${BUILD_NUMBER} && docker push ghcr.io/seagate/cortx-data:${VERSION}-latest
                   docker push ghcr.io/seagate/cortx-control:${VERSION}-${BUILD_NUMBER} && docker push ghcr.io/seagate/cortx-control:${VERSION}-latest

                   docker rmi ghcr.io/seagate/cortx-rgw:${VERSION}-latest
                   docker rmi ghcr.io/seagate/cortx-data:${VERSION}-latest
                   docker rmi ghcr.io/seagate/cortx-control:${VERSION}-latest
                   docker rmi ghcr.io/seagate/cortx-rgw:${VERSION}-${BUILD_NUMBER}
                   docker rmi ghcr.io/seagate/cortx-data:${VERSION}-${BUILD_NUMBER}
                   docker rmi ghcr.io/seagate/cortx-control:${VERSION}-${BUILD_NUMBER}
                '''
           }
        }

        stage ("Changesetlog generation") {
            steps {
                script {
                    def changelog = build job: '/Release_Engineering/Cortx-Automation/changelog-generation', wait: true, propagate: false,
                    parameters: [
                        string(name: 'BUILD_FROM', value: "ghcr.io/seagate/cortx-rgw:${VERSION}-${last_success_build_number}"),
                        string(name: 'BUILD_TO', value: "ghcr.io/seagate/cortx-rgw:${VERSION}-${BUILD_NUMBER}"),
                    ]
                    env.changeset_log_url = changelog.absoluteUrl
                    copyArtifacts filter: 'CHANGESET.txt', fingerprintArtifacts: true, flatten: true, optional: true, projectName: '/Release_Engineering/Cortx-Automation/changelog-generation', selector: lastCompleted(), target: ''
                }
            }
        }

        stage ("K8s QA Sanity") {
            steps {
                script {
                    catchError(stageResult: 'FAILURE') {
                        def qaSanity = build job: '/QA-Sanity-Multinode-RGW', wait: true, propagate: false,
                        parameters: [
                            string(name: 'M_NODE', value: "${env.master_node}"),
                            password(name: 'HOST_PASS', value: "${env.hostpasswd}"),
                            string(name: 'CORTX_IMAGE', value: "ghcr.io/seagate/cortx-rgw:${VERSION}-${BUILD_NUMBER}"),
                            string(name: 'NUM_NODES', value: "${env.numberofnodes}")
                        ]
                        env.Sanity_Failed = qaSanity.buildVariables.Sanity_Failed
                        env.Sanity_status = qaSanity.buildVariables.Sanity_Failed.toString() == 'true' ? 'Failed' : qaSanity.buildVariables.Sanity_Failed.toString() == 'false' ? 'Passed' : 'Skipped'
                        env.Regression_Failed = qaSanity.buildVariables.Regression_Failed
                        env.Regression_overall_status = qaSanity.buildVariables.Regression_overall_failed.toString() == 'true' ? 'Failed' : qaSanity.buildVariables.Regression_overall_failed.toString() == 'false' ? 'Passed' : 'Skipped'
                        env.Io_Path_Failed = qaSanity.buildVariables.Io_Path_Failed
                        env.Failure_Domain_Failed = qaSanity.buildVariables.Failure_Domain_Failed
                        env.sanity_result = qaSanity.currentResult
                        env.Current_TP = qaSanity.buildVariables.Current_TP
                        env.Health = qaSanity.buildVariables.Health
                        env.qaSanity_status = qaSanity.currentResult
                        env.qaSanityK8sJob_URL = qaSanity.absoluteUrl
                        env.totalcount = qaSanity.buildVariables.totalcount
                        env.passcount = qaSanity.buildVariables.passcount
                        env.failcount = qaSanity.buildVariables.failcount
                        env.skipcount = qaSanity.buildVariables.skipcount
                        env.todocount = qaSanity.buildVariables.todocount
                        env.abortcount = qaSanity.buildVariables.abortcount
                    }
                    copyArtifacts filter: 'log/*report.xml', fingerprintArtifacts: true, flatten: true, optional: true, projectName: 'QA-Sanity-Multinode-RGW', selector: lastCompleted(), target: 'log/'
                    copyArtifacts filter: 'log/*report.html', fingerprintArtifacts: true, flatten: true, optional: true, projectName: 'QA-Sanity-Multinode-RGW', selector: lastCompleted(), target: 'log/'
                    copyArtifacts filter: 'log/*report.xml', fingerprintArtifacts: true, flatten: true, optional: true, projectName: 'QA-Sanity-Multinode-RGW', selector: lastCompleted(), target: ''
                    copyArtifacts filter: 'log/*report.html', fingerprintArtifacts: true, flatten: true, optional: true, projectName: 'QA-Sanity-Multinode-RGW', selector: lastCompleted(), target: ''
                }
            }
        }
    }

    post {
        always {
            script {
                junit allowEmptyResults: true, testResults: '*report.xml'
                echo "${env.cortxCluster_status}"
                echo "${env.qaSanity_status}"
                env.changeset_log_url = sh( script: "echo ${env.changeset_log_url}artifact/CHANGESET.txt", returnStdout: true)
                if ( "${env.cortxCluster_status}" == "SUCCESS" && "${env.qaSanity_status}" == "SUCCESS" ) {
                    MESSAGE = "K8s Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=Passed, SanityTest=Passed, Regression=Passed"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                    env.deployment_result = "SUCCESS"
                    env.sanity_result = "SUCCESS"
                    currentBuild.result = "SUCCESS"
                } else if ( "${env.cortxCluster_status}" == "FAILURE" || "${env.cortxCluster_status}" == "UNSTABLE" || "${env.cortxCluster_status}" == "null" ) {
                    manager.buildFailure()
                    MESSAGE = "K8s Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=failed, SanityTest=${env.Sanity_status}, Regression=${env.Regression_overall_status}"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
                    env.sanity_result = "SKIPPED"
                    env.deployment_result = "FAILURE"
                    currentBuild.result = "FAILURE"
                } else if ( "${env.cortxCluster_status}" == "SUCCESS" && "${env.qaSanity_status}" == "FAILURE" || "${env.qaSanity_status}" == "null" ) {
                    manager.buildFailure()
                    MESSAGE = "K8s Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=Passed, SanityTest=${env.Sanity_status}, Regression=${env.Regression_overall_status}"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
                    env.sanity_result = "FAILURE"
                    env.deployment_result = "SUCCESS"
                    currentBuild.result = "FAILURE"
                } else if ( "${env.cortxCluster_status}" == "SUCCESS" && "${env.qaSanity_status}" == "UNSTABLE" ) {
                    MESSAGE = "K8s Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=Passed, SanityTest=${env.Sanity_status}, Regression=${env.Regression_overall_status}"
                    ICON = "unstable.gif"
                    STATUS = "UNSTABLE"
                    env.deployment_result = "SUCCESS"
                    env.sanity_result = "UNSTABLE"
                    currentBuild.result = "UNSTABLE"
                } else {
                    MESSAGE = "K8s Build#${build_id} ${env.numberofnodes}Node Deployment Deployment=unstable, SanityTest=unstable, Regression=unstable"
                    ICON = "unstable.gif"
                    STATUS = "UNSTABLE"
                    env.sanity_result = "UNSTABLE"
                    env.deployment_result = "UNSTABLE"
                    currentBuild.result = "UNSTABLE"
                }
                env.build_setupcortx_url = sh( script: "echo ${env.cortxcluster_build_url}/artifact/artifacts/cortx-cluster-status.txt", returnStdout: true)
                env.host = "${env.allhost}"
                env.build_id = "ghcr.io/seagate/cortx-rgw:${VERSION}-${BUILD_NUMBER}"
                env.build_location = "ghcr.io/seagate/cortx-rgw:${VERSION}-${BUILD_NUMBER},ghcr.io/seagate/cortx-data:${VERSION}-${BUILD_NUMBER},ghcr.io/seagate/cortx-control:${VERSION}-${BUILD_NUMBER}"
                env.deployment_status = "${MESSAGE}"
                env.cluster_status = "${env.build_setupcortx_url}"
                env.cortx_script_branch = "${CORTX_SCRIPTS_BRANCH}"
                env.CORTX_DOCKER_IMAGE = "ghcr.io/seagate/cortx-rgw:${VERSION}-${BUILD_NUMBER}"

                if ( params.EMAIL_RECIPIENTS == "ALL" && currentBuild.result == "SUCCESS" ) {
                    mailRecipients = "CORTX.All@seagate.com"
                    //mailRecipients = "cortx.sme@seagate.com, manoj.management.team@seagate.com, CORTX.SW.Architecture.Team@seagate.com, CORTX.DevOps.RE@seagate.com"
                }
                else if ( params.EMAIL_RECIPIENTS == "ALL" && currentBuild.result == "UNSTABLE" ) {
                    mailRecipients = "Cortx.Perf@seagate.com, sarang.sawant@seagate.com, amit.kapil@seagate.com, amol.j.kongre@seagate.com, deepak.choudhary@seagate.com, jaikumar.gidwani@seagate.com, mandar.joshi@seagate.com, neerav.choudhari@seagate.com, pranay.kumar@seagate.com, swarajya.pendharkar@seagate.com, taizun.a.kachwala@seagate.com, trupti.patil@seagate.com, ujjwal.lanjewar@seagate.com, shailesh.vaidya@seagate.com, abhijit.patil@seagate.com, sonal.kalbende@seagate.com, gaurav.chaudhari@seagate.com, mukul.malhotra@seagate.com, swanand.s.gadre@seagate.com, don.r.bloyer@seagate.com, chandradhar.raval@seagate.com, pankaj.g.borole@seagate.com "
                }
                else if ( params.EMAIL_RECIPIENTS == "ALL" && currentBuild.result == "FAILURE" ) {
                    mailRecipients = "sarang.sawant@seagate.com, amit.kapil@seagate.com, amol.j.kongre@seagate.com, deepak.choudhary@seagate.com, jaikumar.gidwani@seagate.com, mandar.joshi@seagate.com, neerav.choudhari@seagate.com, pranay.kumar@seagate.com, swarajya.pendharkar@seagate.com, taizun.a.kachwala@seagate.com, trupti.patil@seagate.com, ujjwal.lanjewar@seagate.com, shailesh.vaidya@seagate.com, abhijit.patil@seagate.com, sonal.kalbende@seagate.com, gaurav.chaudhari@seagate.com, mukul.malhotra@seagate.com, swanand.s.gadre@seagate.com, don.r.bloyer@seagate.com, chandradhar.raval@seagate.com, pankaj.g.borole@seagate.com "
                }
                else if ( params.EMAIL_RECIPIENTS == "DEVOPS" && currentBuild.result == "SUCCESS" ) {
                    mailRecipients = "CORTX.All@seagate.com, CORTX.DevOps.RE@seagate.com"
                }
                else if ( params.EMAIL_RECIPIENTS == "DEVOPS" && currentBuild.result == "UNSTABLE" ) {
                     mailRecipients = "Cortx.Perf@seagate.com, CORTX.DevOps.RE@seagate.com"
                }
                else if ( params.EMAIL_RECIPIENTS == "DEVOPS" && currentBuild.result == "FAILURE" ) {
                    mailRecipients = "CORTX.DevOps.RE@seagate.com"
                }
                else if ( params.EMAIL_RECIPIENTS == "DEBUG" ) {
                    mailRecipients = "shailesh.vaidya@seagate.com"
                }               

                catchError(stageResult: 'FAILURE') {
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'log/*report.xml, log/*report.html, support_bundle/*.tar, crash_files/*.gz, CHANGESET.txt', followSymlinks: false
                    emailext (
                        body: '''${SCRIPT, template="K8s-deployment-email_3.template"}${SCRIPT, template="REL_QA_SANITY_CUS_EMAIL_7.template"}''',
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

def getBuild(job_url) {
    buildID = sh(script: "curl -XGET -k '${job_url}/api/json' | jq -r '.lastSuccessfulBuild.number'", returnStdout: true).trim()
    return "$buildID"   
}