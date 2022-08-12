pipeline {
    agent {
        node {
            label 'docker-k8-deployment-node'
        }
    }
    
    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    parameters {
        string(name: 'RAW_CORTX_RE_REPO', defaultValue: 'https://raw.githubusercontent.com/Seagate/cortx-re', description: 'Raw URl required for creating K8s resources', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Metrics Server Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Metrics Server Setup scripts', trim: true)
        string(name: 'DASHBOARD_VERSION', defaultValue: 'v2.6.0', description: 'Kubernetes Dashboard version', trim: true)
        string(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'Enter Primary node of your K8s Cluster    ', name: 'hosts')

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

        stage ('Setup Metrics server & K8s dashboard') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'setup metrics server', script: '''
                    pushd solutions/kubernetes/metrics-server/
                        export RAW_CORTX_RE_REPO=${RAW_CORTX_RE_REPO}
                        export CORTX_RE_REPO=${CORTX_RE_REPO}
                        export CORTX_RE_BRANCH=${CORTX_RE_BRANCH}
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        bash setup-metrics-server.sh
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
                if ( fileExists('/var/tmp/cluster-status.txt') && currentBuild.currentResult == "SUCCESS" ) {
                    clusterStatus = readFile(file: '/var/tmp/cluster-status.txt')
                    MESSAGE = "Metrics Server Setup Success for the build ${build_id}"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                } else if ( currentBuild.currentResult == "FAILURE" ) {
                    manager.buildFailure()
                    MESSAGE = "Metrics Server Setup Failed for the build ${build_id}"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
                } else {
                    manager.buildUnstable()
                    MESSAGE = "Metrics Server Setup unstable for the build ${build_id}"
                    ICON = "warning.gif"
                    STATUS = "UNSTABLE"
                }
                
                clusterStatusHTML = "<pre>${clusterStatus}</pre>"

                manager.createSummary("${ICON}").appendText("<h3>K8 Metrics Server Setup ${currentBuild.currentResult} </h3><p>Please check <a href=\"${BUILD_URL}/console\">Metrics Server Setup logs</a> for more info <h4>Cluster Status:</h4>${clusterStatusHTML}", false, false, false, "red")

                // Email Notification
                env.cluster_status = "${clusterStatusHTML}"

                def toEmail = ""
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                if ( manager.build.result.toString() == "FAILURE" ) {
                    toEmail = "shailesh.vaidya@seagate.com"
                    recipientProvidersClass = [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
                }
                emailext ( 
                    body: '''${SCRIPT, template="cluster-setup-email.template"}''',
                    mimeType: 'text/html',
                    subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                    attachLog: true,
                    to: toEmail,
                    recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}