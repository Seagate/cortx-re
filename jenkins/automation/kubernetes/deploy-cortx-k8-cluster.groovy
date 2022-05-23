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
        PODS_ON_PRIMARY = "true"
    }


    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Cluster scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for CORTX Cluster scripts', trim: true)
        string(name: 'CORTX_SERVER_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-rgw:2.0.0-latest', description: 'CORTX-SERVER image', trim: true)
        string(name: 'CORTX_DATA_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-data:2.0.0-latest', description: 'CORTX-DATA image', trim: true)
        string(name: 'CORTX_CONTROL_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-control:2.0.0-latest', description: 'CORTX-CONTROL image', trim: true)
        choice(
            name: 'DEPLOYMENT_METHOD',
            choices: ['standard', 'data-only'],
            description: 'Method to deploy required CORTX service. standard method will deploy all CORTX services'
        )
        string(name: 'SNS_CONFIG', defaultValue: '1+0+0', description: 'sns configuration for deployment. Please select value based on disks available on nodes.', trim: true)
        string(name: 'DIX_CONFIG', defaultValue: '1+0+0', description: 'dix configuration for deployment. Please select value based on disks available on nodes.', trim: true)
        string(name: 'CONTROL_EXTERNAL_NODEPORT', defaultValue: '31169', description: 'Port to be used for control service.', trim: true)
        string(name: 'S3_EXTERNAL_HTTP_NODEPORT', defaultValue: '30080', description: 'HTTP Port to be used for IO service.', trim: true)
        string(name: 'S3_EXTERNAL_HTTPS_NODEPORT', defaultValue: '30443', description: 'HTTPS to be used for IO service.', trim: true)
        string(name: 'NAMESPACE', defaultValue: 'cortx', description: 'kubernetes namespace to be used for CORTX deployment.', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VM details to be used. First node will be used as Primary node', name: 'hosts')
        choice(
            name: 'EXTERNAL_EXPOSURE_SERVICE',
            choices: ['LoadBalancer', 'NodePort'],
            description: 'K8s Service to be used to expose RGW Service to outside cluster.'
        )
        // Please configure CORTX_SCRIPTS_BRANCH and CORTX_SCRIPTS_REPO parameter in Jenkins job configuration.
    }    

    stages {

        stage('Checkout Script') {
            steps { 
                cleanWs()            
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])                
                }
            }
        }

        stage ('Setup K8s Cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Setup K8s Cluster', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        if [ "$(cat hosts | wc -l)" -eq 2 ]
                        then
                           echo "Current configuration does not support 2 node CORTX cluster deployment. Please try with 1 or more than two nodes."
                           echo "Exiting Jenkins job."
                           exit 1
                        fi
                        ./cluster-setup.sh ${PODS_ON_PRIMARY}
                    popd
                '''
            }
        }

        stage ('Deploy CORTX') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploy CORTX Components', script: '''
                    pushd solutions/kubernetes/
                        export SOLUTION_CONFIG_TYPE=automated
                        export CORTX_SCRIPTS_BRANCH=${CORTX_SCRIPTS_BRANCH}
                        export CORTX_SCRIPTS_REPO=${CORTX_SCRIPTS_REPO}
                        export CORTX_SERVER_IMAGE=${CORTX_SERVER_IMAGE}
                        export CORTX_DATA_IMAGE=${CORTX_DATA_IMAGE}
                        export CORTX_CONTROL_IMAGE=${CORTX_CONTROL_IMAGE}
                        export DEPLOYMENT_METHOD=${DEPLOYMENT_METHOD}
                        export SNS_CONFIG=${SNS_CONFIG}
                        export DIX_CONFIG=${DIX_CONFIG}
                        export EXTERNAL_EXPOSURE_SERVICE=${EXTERNAL_EXPOSURE_SERVICE}
                        export CONTROL_EXTERNAL_NODEPORT=${CONTROL_EXTERNAL_NODEPORT}
                        export S3_EXTERNAL_HTTP_NODEPORT=${S3_EXTERNAL_HTTP_NODEPORT}
                        export S3_EXTERNAL_HTTPS_NODEPORT=${S3_EXTERNAL_HTTPS_NODEPORT}
                        export NAMESPACE=${NAMESPACE}
                        ./cortx-deploy.sh --cortx-cluster
                    popd
                '''
            }
        }
    }

    post {
        always {

            script {

                // Jenkins Summary
                clusterStatus = ""
                if ( fileExists('/var/tmp/cortx-cluster-status.txt') && currentBuild.currentResult == "SUCCESS" ) {
                    clusterStatus = readFile(file: '/var/tmp/cortx-cluster-status.txt')
                    MESSAGE = "CORTX Cluster Setup Success for the build ${build_id}"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                } else if ( currentBuild.currentResult == "FAILURE" ) {
                    manager.buildFailure()
                    MESSAGE = "CORTX Cluster Setup Failed for the build ${build_id}"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
 
                } else {
                    manager.buildUnstable()
                    MESSAGE = "CORTX Cluster Setup is Unstable"
                    ICON = "warning.gif"
                    STATUS = "UNSTABLE"
                }
                
                clusterStatusHTML = "<pre>${clusterStatus}</pre>"

                manager.createSummary("${ICON}").appendText("<h3>CORTX Cluster Setup ${currentBuild.currentResult} </h3><p>Please check <a href=\"${BUILD_URL}/console\">cluster setup logs</a> for more info <h4>Cluster Status:</h4>${clusterStatusHTML}", false, false, false, "red")

                // Email Notification
                env.cluster_status = "${clusterStatusHTML}"
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                mailRecipients = "shailesh.vaidya@seagate.com"
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

        cleanup {
            sh label: 'Collect Artifacts', script: '''
            mkdir -p artifacts
            pushd solutions/kubernetes/
                HOST_FILE=$PWD/hosts
                PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
                scp -q "$PRIMARY_NODE":/root/deploy-scripts/k8_cortx_cloud/solution.yaml $WORKSPACE/artifacts/
                if [ -f /var/tmp/cortx-cluster-status.txt ]; then
                    cp /var/tmp/cortx-cluster-status.txt $WORKSPACE/artifacts/
                fi
            popd    
            '''
            script {
                // Archive Deployment artifacts in jenkins build
                archiveArtifacts artifacts: "artifacts/*.*", onlyIfSuccessful: false, allowEmptyArchive: true 
            }    
        }

        failure {
            sh label: 'Collect CORTX support bundle logs in artifacts', script: '''
            mkdir -p artifacts
            pushd solutions/kubernetes/
                ./cortx-deploy.sh --support-bundle
                HOST_FILE=$PWD/hosts
                PRIMARY_NODE=$(head -1 "$HOST_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
                LOG_FILE=$(ssh -o 'StrictHostKeyChecking=no' $PRIMARY_NODE 'ls -t /root/deploy-scripts/k8_cortx_cloud | grep logs-cortx-cloud | grep .tar | head -1')
                scp -q "$PRIMARY_NODE":/root/deploy-scripts/k8_cortx_cloud/$LOG_FILE $WORKSPACE/artifacts/
            popd
            '''
        }  
    }
}
