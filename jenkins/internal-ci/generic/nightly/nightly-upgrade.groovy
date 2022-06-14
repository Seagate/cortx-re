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
                    env.preupgrade_cortx_server_image = upgradeCluster.buildVariables.preupgrade_cortx_server_image
                }
            }
        }

        stage('Chnagelog Generation') {
            steps {
                script {
                    def changelog = build job: '/Release_Engineering/Cortx-Automation/changelog-generation', wait: true, propagate: false,
                    parameters: [
                        string(name: 'BUILD_FROM', value: "${env.preupgrade_cortx_server_image}"),
                        string(name: 'BUILD_TO', value: "${CORTX_SERVER_IMAGE}"),
                    ]
                    env.changeset_log_url = changelog.absoluteUrl
                    copyArtifacts filter: 'CHANGESET.txt', fingerprintArtifacts: true, flatten: true, optional: true, projectName: '/Release_Engineering/Cortx-Automation/changelog-generation', selector: lastCompleted(), target: ''
                }
            }
        }

        stage('QA SAnity') {
            steps {
                script {
                    echo "QA Sanity job to be added"
                }
            }        
        }
    }
}    