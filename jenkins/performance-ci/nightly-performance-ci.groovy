pipeline {
    agent {
        node {
            label 'docker-centos-7.9.2009-node'
        }
    }

    triggers { cron('30 20 * * *') }

    options {
        timeout(time: 180, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '20', numToKeepStr: '20'))
        disableConcurrentBuilds()
    }
    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_SERVER_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-rgw:2.0.0-latest', description: 'CORTX-RGW image', trim: true)
        string(name: 'CORTX_DATA_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-data:2.0.0-latest', description: 'CORTX-DATA image', trim: true)
        string(name: 'CORTX_CONTROL_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-control:2.0.0-latest', description: 'CORTX-CONTROL image', trim: true)
        string(name: 'CORTX_TOOLS_BRANCH', defaultValue: 'sanity-fix', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_TOOLS_REPO', defaultValue: 'shailesh-vaidya/seagate-tools', description: 'Repository for Cluster Setup scripts', trim: true)
        choice (
            choices: ['DEVOPS', 'ALL', 'DEBUG'],
            description: 'Email Notification Recipients ',
            name: 'EMAIL_RECIPIENTS'
        )

        // Please configure hosts, client_node, SNS and DIX parameter in Jenkins job configuration..

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
                        string(name: 'EXTERNAL_EXPOSURE_SERVICE', value: "NodePort"),
                        string(name: 'SNS_CONFIG', value: "${SNS_CONFIG}"),
                        string(name: 'DIX_CONFIG', value: "${DIX_CONFIG}")
                    ]
                    env.cortxcluster_build_url = cortxCluster.absoluteUrl
                    env.cortxCluster_status = cortxCluster.currentResult
                }
            }
        }

        stage ('Execute performace sanity') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def cortxCluster = build job: '/Cortx-Automation/Performance/run-performance-sanity/', wait: true,
                    parameters: [
                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                        string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                        string(name: 'CORTX_TOOLS_BRANCH', value: "${CORTX_TOOLS_BRANCH}"),
                        string(name: 'CORTX_TOOLS_REPO', value: "${CORTX_TOOLS_REPO}"),
                        text(name: 'primary_nodes', value: "${env.primarynodes}"),
                        text(name: 'client_nodes', value: "${client_nodes}")
                    ]
                    copyArtifacts filter: 'artifacts/perf*    ', fingerprintArtifacts: true, flatten: true, optional: true, projectName: '/Cortx-Automation/Performance/run-performance-sanity/', selector: lastCompleted(), target: ''
                }
            }
        }
    }

    post {

        cleanup {
            script {
                // Archive Deployment artifacts in jenkins build
                archiveArtifacts artifacts: "perf*", onlyIfSuccessful: false, allowEmptyArchive: true 
            }
        }

        always { 
            script {
                // Jenkins Summary
                clusterStatus = ""
                if ( currentBuild.currentResult == "SUCCESS" ) {
                    clusterStatus = readFile(file: 'perf_sanity_stats.txt')
                    MESSAGE = "Build#${build_id} Nightly CORTX Performance CI Success"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                } else if ( currentBuild.currentResult == "FAILURE" ) {
                    manager.buildFailure()
                    MESSAGE = "Build#${build_id} Nightly CORTX Performance CI Failed"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
 
                } else {
                    manager.buildUnstable()
                    MESSAGE = "Build#${build_id} Nightly CORTX Performance CI Unstable"
                    ICON = "warning.gif"
                    STATUS = "UNSTABLE"
                }
                
                clusterStatusHTML = "<pre>${clusterStatus}</pre>"

                manager.createSummary("${ICON}").appendText("<h3>Nightly CORTX Performance CI ${currentBuild.currentResult} </h3><p>Please check <a href=\"${BUILD_URL}/console\">Performance Sanity Execution logs</a> for more info <h4>Sanity Execution Logs:</h4>${clusterStatusHTML}", false, false, false, "red")

                // Email Notification
                if ( params.EMAIL_RECIPIENTS == "DEVOPS" && currentBuild.result == "SUCCESS" ) {
                    mailRecipients = "CORTX.DevOps.RE@seagate.com, CORTX.Perf@seagate.com"
                }
                else if ( params.EMAIL_RECIPIENTS == "DEBUG" ) {
                    mailRecipients = "shailesh.vaidya@seagate.com"
                }

                env.build_stage = "${build_stage}"
                env.cluster_status = "${clusterStatusHTML}"
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                emailext ( 
                    body: '''${SCRIPT, template="cluster-setup-email.template"}''',
                    mimeType: 'text/html',
                    subject: "Build#${build_id} Nightly CORTX Performance CI ${currentBuild.currentResult}",
                    attachLog: true,
                    to: "${mailRecipients}",
                    recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}