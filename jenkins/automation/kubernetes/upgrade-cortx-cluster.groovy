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
        string(name: 'CORTX_SERVER_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-rgw:2.0.0-latest', description: 'CORTX-SERVER image', trim: true)
        string(name: 'CORTX_DATA_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-data:2.0.0-latest', description: 'CORTX-DATA image', trim: true)
        string(name: 'CORTX_CONTROL_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-control:2.0.0-latest', description: 'CORTX-CONTROL image', trim: true)
        choice(
            name: 'POD_TYPE',
            choices: ['all', 'data', 'control', 'ha', 'server'],
            description: 'Pods need to be upgraded. option all will upgrade all CORTX services pods'
        )
        choice(
            name: 'DEPLOYMENT_METHOD',
            choices: ['standard', 'data-only'],
            description: 'Method to deploy required CORTX service. standard method will deploy all CORTX services'
        )
        choice(
            name: 'UPGRADE_TYPE',
            choices: ['rolling-upgrade', 'cold-upgrade'],
            description: 'Method to upgrade required CORTX cluster.'
        )
        // Please configure hosts, CORTX_SCRIPTS_BRANCH and CORTX_SCRIPTS_REPO parameter in Jenkins job configuration.
    }

    stages {
        
        stage('Checkout Script') {
            script { build_stage = env.STAGE_NAME }
            steps { 
                cleanWs()            
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])                
                }
            }
        }

        stage('Pre-Upgrade Cluster Status') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'fetch cluster status', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' | head -n 1 > hosts
                        cat hosts
                        ./cortx-upgrade.sh --cluster-status
                    popd
                '''
            }
        }

        stage('Upgrade Cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Upgrade cluster', script: '''
                    pushd solutions/kubernetes/
                        export CORTX_SCRIPTS_BRANCH=${CORTX_SCRIPTS_BRANCH}
                        export CORTX_SCRIPTS_REPO=${CORTX_SCRIPTS_REPO}
                        export CORTX_SERVER_IMAGE=${CORTX_SERVER_IMAGE}
                        export CORTX_DATA_IMAGE=${CORTX_DATA_IMAGE}
                        export CORTX_CONTROL_IMAGE=${CORTX_CONTROL_IMAGE}
                        export SOLUTION_CONFIG_TYPE="automated"
                        export POD_TYPE=${POD_TYPE}
                        export DEPLOYMENT_METHOD=${DEPLOYMENT_METHOD}
                        export UPGRADE_TYPE=${UPGRADE_TYPE}
                        ./cortx-upgrade.sh --upgrade
                    popd
                '''
            }
        }

        stage('Post-Upgrade Cluster Status') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'fetch cluster status', script: '''
                    pushd solutions/kubernetes/
                        ./cortx-upgrade.sh --cluster-status
                    popd
                '''
            }
        }

        stage('Post-Upgrade IO Sanity') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'execute IO operations', script: '''
                    pushd solutions/kubernetes/
                        ./cortx-upgrade.sh --io-sanity
                    popd
                '''
            }
        }
    }
    post {
        always {
            script {
                clusterStatus = ""
                upgradeStatus = ""
                if ( fileExists('/var/tmp/upgrade-logs.txt') && fileExists('/var/tmp/cortx-cluster-status.txt') && currentBuild.currentResult == "SUCCESS" ) {
                    clusterStatus = readFile(file: '/var/tmp/cortx-cluster-status.txt')
                    upgradeStatus = readFile(file: '/var/tmp/upgrade-logs.txt')
                    MESSAGE = "CORTX Cluster Upgrade Success for the build ${build_id}"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                } else if ( currentBuild.currentResult == "FAILURE" ) {
                    manager.buildFailure()
                    MESSAGE = "CORTX Cluster Upgrade Failed for the build ${build_id}"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
 
                } else {
                    manager.buildUnstable()
                    MESSAGE = "CORTX Cluster Upgrade is Unstable"
                    ICON = "warning.gif"
                    STATUS = "UNSTABLE"
                }

                manager.createSummary("${ICON}").appendText("<h3>CORTX Cluster Setup ${currentBuild.currentResult} </h3><p>Please check <a href=\"${BUILD_URL}/console\">cluster setup logs</a> for more info <h4>Cluster Status:</h4>${clusterStatusHTML}", false, false, false, "red")

                // Email Notification
                env.build_stage = "${build_stage}"
                env.cluster_status = sh( script: "echo ${build_url}/artifact/artifacts/cortx-cluster-status.txt", returnStdout: true)
                env.upgrade_logs = sh( script: "echo ${build_url}/artifact/artifacts/upgrade-logs.txt", returnStdout: true)
                env.cortx_script_branch = "${CORTX_SCRIPTS_BRANCH}"
                env.hosts = "${hosts}"
                env.images_info = "${CORTX_SERVER_IMAGE},${CORTX_DATA_IMAGE},${CORTX_CONTROL_IMAGE}"
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                mailRecipients = "gaurav.chaudhari@seagate.com"
                emailext ( 
                    body: '''${SCRIPT, template="cluster-upgrade-email.template"}''',
                    mimeType: 'text/html',
                    subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                    attachLog: true,
                    to: "${mailRecipients}",
                    recipientProviders: recipientProvidersClass
                )
            }
        }
        
        cleanup {
            sh label: 'Collect Artifacts', script: '''
            mkdir -p artifacts
            pushd solutions/kubernetes/
                HOST_FILE=$PWD/hosts
                PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
                [ -f /var/tmp/cortx-cluster-status.txt ] && cp /var/tmp/cortx-cluster-status.txt $WORKSPACE/artifacts/
                [ -f /var/tmp/upgrade-logs.txt ] && cp /var/tmp/upgrade-logs.txt $WORKSPACE/artifacts/
                scp -q "$PRIMARY_NODE":/root/deploy-scripts/k8_cortx_cloud/solution.yaml $WORKSPACE/artifacts/
            popd    
            '''
            script {
                // Archive Deployment artifacts in jenkins build
                archiveArtifacts artifacts: "artifacts/*.*", onlyIfSuccessful: false, allowEmptyArchive: true 
            }
        }
    }        
}        