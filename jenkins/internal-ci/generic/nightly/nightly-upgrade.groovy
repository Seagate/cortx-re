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
        last_successful_server_image = getLastSuccessfulImage("cortx-rgw", JOB_URL)
        last_successful_data_image = getLastSuccessfulImage("cortx-data", JOB_URL)
        last_successful_control_image = getLastSuccessfulImage("cortx-control", JOB_URL)
    }

    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_SERVER_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-rgw:2.0.0-latest', description: 'CORTX-SERVER image for cluster upgrade', trim: true)
        string(name: 'CORTX_DATA_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-data:2.0.0-latest', description: 'CORTX-DATA image for cluster upgrade', trim: true)
        string(name: 'CORTX_CONTROL_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-control:2.0.0-latest', description: 'CORTX-CONTROL image for cluster upgrade', trim: true)
        choice(
            name: 'POD_TYPE',
            choices: ['all', 'data', 'control', 'ha', 'server'],
            description: 'Pods need to be upgraded. option all will upgrade all CORTX services pods'
        )
        choice(
            name: 'DEPLOYMENT_METHOD',
            choices: ['standard', 'data-only'],
            description: 'Method using which CORTX deployment is done. default is standard method'
        )
        choice(
            name: 'UPGRADE_TYPE',
            choices: ['rolling-upgrade', 'cold-upgrade'],
            description: 'Method to upgrade required CORTX cluster.'
        )
        // Please configure hosts, CORTX_SCRIPTS_BRANCH and CORTX_SCRIPTS_REPO parameter in Jenkins job configuration.
    }

    stages {
        stage('Upgrade CORTX Cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }            
                script {
                    def upgradeCluster = build job: 'Cortx-Automation/RGW/cortx-rgw-cluster-upgrade/', wait: true,
                    parameters: [
                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                        string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                        string(name: 'CORTX_SERVER_IMAGE', value: "${CORTX_SERVER_IMAGE}"),
                        string(name: 'CORTX_DATA_IMAGE', value: "${CORTX_DATA_IMAGE}"),
                        string(name: 'CORTX_CONTROL_IMAGE', value: "${CORTX_CONTROL_IMAGE}"),
                        string(name: 'DEPLOYMENT_METHOD', value: "${DEPLOYMENT_METHOD}"),
                        string(name: 'POD_TYPE', value: "${POD_TYPE}"),
                        text(name: 'hosts', value: "${hosts}"),
                        string(name: 'CORTX_SCRIPTS_BRANCH', value: "${CORTX_SCRIPTS_BRANCH}"),
                        string(name: 'CORTX_SCRIPTS_REPO', value: "${CORTX_SCRIPTS_REPO}")
                    ]
                    env.upgradecluster_build_url = upgradeCluster.absoluteUrl
                    env.upgradeCluster_status = upgradeCluster.currentResult
                    env.images_info = upgradeCluster.buildVariables.images_info
                    env.actual_server_image = upgradeCluster.buildVariables.postupgrade_cortx_server_image
                    copyArtifacts filter: 'artifacts/cortx-cluster-status.txt', fingerprintArtifacts: true, flatten: true, optional: true, projectName: '/Cortx-Automation/RGW/cortx-rgw-cluster-upgrade/', selector: lastCompleted(), target: ''
                    copyArtifacts filter: 'artifacts/upgrade-logs.txt', fingerprintArtifacts: true, flatten: true, optional: true, projectName: '/Cortx-Automation/RGW/cortx-rgw-cluster-upgrade/', selector: lastCompleted(), target: ''
                    // env.preupgrade_cortx_server_image = upgradeCluster.buildVariables.preupgrade_cortx_server_image
                    // env.preupgrade_images_info = upgradeCluster.buildVariables.preupgrade_images_info
                }
            }
        }

        stage('Chnagelog Generation') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def changelog = build job: '/Release_Engineering/Cortx-Automation/changelog-generation', wait: true, propagate: false,
                    parameters: [
                        string(name: 'BUILD_FROM', value: "${last_successful_server_image}"),
                        string(name: 'BUILD_TO', value: "${env.actual_server_image}"),
                    ]
                    env.changeset_log_url = changelog.absoluteUrl
                    copyArtifacts filter: 'CHANGESET.txt', fingerprintArtifacts: true, flatten: true, optional: true, projectName: '/Release_Engineering/Cortx-Automation/changelog-generation', selector: lastCompleted(), target: ''
                }
            }
        }

        stage('QA SAnity') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    echo "QA Sanity job to be added"
                    env.qaSanity_status = "SUCCESS"
                }
            }        
        }
    }

    post {
        always {
            script {
                if ( "${env.upgradeCluster_status}" == "SUCCESS" ) {
                    MESSAGE = "Build#${build_id} CORTX Cluster Upgrade Upgrade=Passed"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                    env.deployment_result = "SUCCESS"
                    env.sanity_result = "SUCCESS"
                    currentBuild.result = "SUCCESS"
                } else if ( "${env.upgradeCluster_status}" == "FAILURE" || "${env.upgradeCluster_status}" == "UNSTABLE" || "${env.upgradeCluster_status}" == "null" ) {
                    manager.buildFailure()
                    MESSAGE = "Build#${build_id} CORTX Cluster Upgrade Upgrade=failed"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
                    env.deployment_result = "FAILURE"
                    currentBuild.result = "FAILURE"
                } else {
                    MESSAGE = "Build#${build_id} CORTX Cluster Upgrade Upgrade=unstable"
                    ICON = "unstable.gif"
                    STATUS = "UNSTABLE"
                    env.deployment_result = "UNSTABLE"
                    currentBuild.result = "UNSTABLE"
                }

                // Email Notification
                env.build_stage = "${build_stage}"
                env.cluster_status = sh( script: "echo ${env.upgradecluster_build_url}/artifact/artifacts/cortx-cluster-status.txt", returnStdout: true)
                env.upgrade_logs = sh( script: "echo ${env.upgradecluster_build_url}/artifact/artifacts/upgrade-logs.txt", returnStdout: true)
                env.changeset_log_url = sh( script: "echo ${env.changeset_log_url}artifact/CHANGESET.txt", returnStdout: true)
                env.preupgrade_images_info = "${last_successful_server_image},${last_successful_data_image},${last_successful_control_image}" 
                env.cortx_script_branch = "${CORTX_SCRIPTS_BRANCH}"
                env.hosts = sh( script: '''
                    echo $hosts | tr ' ' '\n' | awk -F["="] '{print $2}'|cut -d',' -f1
                ''', returnStdout: true).trim()
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                if ( currentBuild.result == "SUCCESS" ) {
                    mailRecipients = "CORTX.DevOps.RE@seagate.com"
                }
                else {
                    mailRecipients = "CORTX.DevOps.RE@seagate.com"        
                }

                archiveArtifacts allowEmptyArchive: true, artifacts: '*.txt', followSymlinks: false
                
                emailext ( 
                    body: '''${SCRIPT, template="nightly-upgrade-email.template"}''',
                    mimeType: 'text/html',
                    subject: "${MESSAGE}",
                    attachLog: true,
                    to: "${mailRecipients}",
                    recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}

def getLastSuccessfulImage(String service, String jobUrl) {
    IMAGE = sh( script: """
        wget --no-check-certificate ${jobUrl}/lastSuccessfulBuild/artifact/cortx-cluster-status.txt &> /dev/null
        grep -i 'seagate/${service}' < cortx-cluster-status.txt | head -n 1
    """, returnStdout: true).trim()
    return "$IMAGE"
}