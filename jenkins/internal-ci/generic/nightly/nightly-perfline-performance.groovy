pipeline {
    agent {
        node {
            label 'docker-centos-7.9.2009-node'
        }
    }

    triggers { cron('30 20 * * *') }

    options {
        timeout(time: 1440, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '20', numToKeepStr: '20'))
        disableConcurrentBuilds()
    }
    parameters {
        string(name: 'SEAGATE_TOOLS_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Perfline deployment', trim: true )
        string(name: 'SEAGATE_TOOLS_REPO', defaultValue: 'https://github.com/Seagate/seagate-tools.git', description: 'Repository for Perfline Setup scripts', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_SERVER_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-rgw:2.0.0-latest', description: 'CORTX-SERVER image', trim: true)
        string(name: 'CORTX_DATA_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-data:2.0.0-latest', description: 'CORTX-DATA image', trim: true)
        string(name: 'CORTX_CONTROL_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-control:2.0.0-latest', description: 'CORTX-CONTROL image', trim: true)
        choice(
            name: 'DEPLOYMENT_METHOD',
            choices: ['data-only', 'standard'],
            description: 'Method to deploy required CORTX service. standard method will deploy all CORTX services'
        )
        string(name: 'CONTROL_EXTERNAL_NODEPORT', defaultValue: '31169', description: 'Port to be used for control service.', trim: true)
        string(name: 'S3_EXTERNAL_HTTP_NODEPORT', defaultValue: '30080', description: 'HTTP Port to be used for IO service.', trim: true)
        string(name: 'S3_EXTERNAL_HTTPS_NODEPORT', defaultValue: '30443', description: 'HTTPS to be used for IO service.', trim: true)
        string(name: 'NAMESPACE', defaultValue: 'cortx', description: 'kubernetes namespace to be used for CORTX deployment.', trim: true)
        choice(
            name: 'EXTERNAL_EXPOSURE_SERVICE',
            choices: ['NodePort', 'LoadBalancer'],
            description: 'K8s Service to be used to expose RGW Service to outside cluster.'
        )
        string(name: 'PERFLINE_WORKLOADS_DIR', defaultValue: "/root/perfline/wrapper/workload/jenkins/standard", description: 'specify the location of your workload directory', trim: true)
        choice (
            choices: ['DEBUG', 'ALL'],
            description: 'Email Notification Recipients ',
            name: 'EMAIL_RECIPIENTS'
        )

        // Please configure hosts, client_node, SNS and DIX parameter in Jenkins job configuration manually.

    }
    stages {
        stage ("Define Build Variables") {
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
                    echo $hosts | tr ' ' '\n' | tail -n +1 | wc -l
                    ''', returnStdout: true).trim()

                    env.primarynodes = sh( script: '''
                    echo $hosts | tr ' ' '\n' | head -1
                    ''', returnStdout: true).trim()
                }
            }
        }

        stage ('Perform pre-defined perfline workloads') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def cortxCluster = build job: '/Motr/cortx-perfline/', wait: true,
                    parameters: [
                        string(name: 'SEAGATE_TOOLS_BRANCH', value: "${SEAGATE_TOOLS_BRANCH}"),
                        string(name: 'SEAGATE_TOOLS_REPO', value: "${SEAGATE_TOOLS_REPO}"),
                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                        string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                        string(name: 'CORTX_SCRIPTS_BRANCH', value: "${CORTX_SCRIPTS_BRANCH}"),
                        string(name: 'CORTX_SERVER_IMAGE', value: "${CORTX_SERVER_IMAGE}"),
                        string(name: 'CORTX_DATA_IMAGE', value: "${CORTX_DATA_IMAGE}"),
                        string(name: 'CORTX_CONTROL_IMAGE', value: "${CORTX_CONTROL_IMAGE}"),
                        string(name: 'SNS_CONFIG', value: "${SNS_CONFIG}"),
                        string(name: 'DIX_CONFIG', value: "${DIX_CONFIG}"),
                        string(name: 'CONTROL_EXTERNAL_NODEPORT', value: "${CONTROL_EXTERNAL_NODEPORT}"),
                        string(name: 'S3_EXTERNAL_HTTP_NODEPORT', value: "${S3_EXTERNAL_HTTP_NODEPORT}"),
                        string(name: 'S3_EXTERNAL_HTTPS_NODEPORT', value: "${S3_EXTERNAL_HTTPS_NODEPORT}"),
                        string(name: 'NAMESPACE', value: "${NAMESPACE}"),
                        string(name: 'PERFLINE_WORKLOADS_DIR', value: "${PERFLINE_WORKLOADS_DIR}"),
                        string(name: 'SYSTEM_DRIVE', value: "${SYSTEM_DRIVE}"),
                        text(name: 'hosts', value: "${hosts}"),
                    ]
                    copyArtifacts filter: 'artifacts/perfline*', fingerprintArtifacts: true, flatten: true, optional: true, projectName: '/Motr/cortx-perfline/', selector: lastCompleted(), target: ''
                }
            }
        }
    }

    post {
        cleanup {
            script {
                // Archive Deployment artifacts in jenkins build
                archiveArtifacts artifacts: "perfline*", onlyIfSuccessful: false, allowEmptyArchive: true
            }
        }
        always {
            script {
                // Jenkins Summary
                clusterStatus = ""
                if ( fileExists('perfline_task_status.txt') && fileExists('perfline_task_list.txt') && currentBuild.currentResult == "SUCCESS") {
                    clusterStatus = readFile(file: 'perfline_task_status.txt')
                    MESSAGE = "Build#${build_id} Nightly Perfline Performance CI Success: <a href=\"http://${env.master_node}:8005/#!/results\">results</a>"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                } else if ( fileExists('perfline_task_list.txt') && currentBuild.currentResult == "FAILURE" ) {
                    manager.buildFailure()
                    clusterStatus = readFile(file: 'task_list.txt')
                    MESSAGE = "Build#${build_id} Nightly Perfline Performance CI Failed: <a href=\"http://${env.master_node}:8005/#!/results\">results</a>"
                    ICON = "error.gif"
                    STATUS = "FAILURE"

                } else {
                    manager.buildUnstable()
                    MESSAGE = "Build#${build_id} Nightly Perfline Performance CI Unstable: <a href=\"http://${env.master_node}:8005/#!/results\">results</a>"
                    ICON = "warning.gif"
                    STATUS = "UNSTABLE"
                }

                PerflineWorkloadStatusHTML = "<pre>${clusterStatus}</pre>"

                manager.createSummary("${ICON}").appendText("<h3>Nightly Perfline Performance CI ${currentBuild.currentResult} </h3><p>Please check <a href=\"${BUILD_URL}/console\">Perfline workload Execution logs</a> for more info <a href=\"http://${env.master_node}:8005/#!/results\">results</a>/<h4>workload Execution Logs:</h4>${PerflineWorkloadStatusHTML}", false, false, false, "red")

                // Email Notification
                if ( params.EMAIL_RECIPIENTS == "ALL" && currentBuild.result == "SUCCESS" ) {
                    mailRecipients = "Motr4-Scrum-Team@seagate.com, CORTX.DevOps.RE@seagate.com"
                }
                else if ( params.EMAIL_RECIPIENTS == "DEBUG" ) {
                    mailRecipients = "rahul.kumar@seagate.com"
                }
                env.build_stage = "${build_stage}"
                env.cluster_status = "${PerflineWorkloadStatusHTML}"
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                emailext (
                    body: '''${SCRIPT, template="cluster-setup-email.template"}''',
                    mimeType: 'text/html',
                    subject: "Build#${build_id} Nightly Perfline Performance CI ${currentBuild.currentResult}",
                    attachLog: true,
                    to: "${mailRecipients}",
                    recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}
