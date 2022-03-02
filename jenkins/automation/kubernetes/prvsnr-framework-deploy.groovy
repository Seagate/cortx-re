pipeline {
    agent {
        node {
            label 'docker-k8-deployment-node'
        }
    }

    triggers { cron('30 19 * * *') }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    environment {
        GITHUB_CRED = credentials('shailesh-github-token')
    }


    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_PRVSNR_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_PRVSNR_REPO', defaultValue: 'Seagate/cortx-prvsnr', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-all:2.0.0-latest', description: 'CORTX-ALL image', trim: true)
        // Please configure hosts parameter in the Jenkins job configuration.
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

        stage ('Destory Pre-existing Cluster Deployment') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Destroy existing cluster deployment:', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        export CORTX_PRVSNR_BRANCH=${CORTX_PRVSNR_BRANCH}
                        export CORTX_PRVSNR_REPO=${CORTX_PRVSNR_REPO}
                        export CORTX_IMAGE=${CORTX_IMAGE}
                        export SOLUTION_CONFIG_TYPE=automated
                        ./prvsnr-framework.sh --destroy-cluster
                    popd
                '''
            }
        }

        stage ('Deploy Provisioner Deployment POD') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploy Provisioner Deployment POD', script: '''
                    pushd solutions/kubernetes/
                        export CORTX_PRVSNR_BRANCH=${CORTX_PRVSNR_BRANCH}
                        export CORTX_PRVSNR_REPO=${CORTX_PRVSNR_REPO}
                        export CORTX_IMAGE=${CORTX_IMAGE}
                        export SOLUTION_CONFIG_TYPE=automated
                        export DEPLOYMENT_TYPE=provisioner
                        ./prvsnr-framework.sh --deploy-cluster
                    popd
                '''
            }
        }

        stage ('IO Sanity Test') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Perform IO Sanity Test', script: '''
                    pushd solutions/kubernetes/
                        export DEPLOYMENT_TYPE=provisioner
                        ./cortx-deploy.sh --io-test
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
                env.build_stage = "${build_stage}"
                env.cluster_status = "${clusterStatusHTML}"
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                mailRecipients = "shailesh.vaidya@seagate.com, CORTX.Provisioner@seagate.com"
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
            cp /var/tmp/cortx-cluster-status.txt $WORKSPACE/artifacts/
            '''
            script {
                // Archive Deployment artifacts in jenkins build
                archiveArtifacts artifacts: "artifacts/*.*", onlyIfSuccessful: false, allowEmptyArchive: true 
            }
        }
    }
}