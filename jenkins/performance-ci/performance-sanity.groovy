pipeline {
    agent {
        node {
            label "performance-sanity-${infrastructure}-client"
        }
    }

    options {
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_TOOLS_BRANCH', defaultValue: 'main', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_TOOLS_REPO', defaultValue: 'Seagate/seagate-tools', description: 'Repository for Cluster Setup scripts', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'CORTX Cluster Primary node details', name: 'primary_nodes')
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'Client node details', name: 'client_nodes')
        // Please configure DB_SERVER and DB_PORT parameters in Jenkins configuration manually
    }

    environment {
        GITHUB_CRED = credentials('shailesh-github-token')
        DB_CRED = credentials('performance_db')
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

        stage ('Execute performace sanity') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Execute performace sanity', script: '''
                    pushd scripts/performance
                        echo $client_nodes | tr ' ' '\n' > client_nodes
                        echo $primary_nodes | tr ' ' '\n' > primary_nodes
                        export GITHUB_TOKEN=${GITHUB_CRED}
                        export CORTX_TOOLS_REPO=${CORTX_TOOLS_REPO}
                        export CORTX_TOOLS_BRANCH=${CORTX_TOOLS_BRANCH}
                        export DB_SERVER=${DB_SERVER}
                        export DB_PORT=${DB_PORT}
                        export DB_USER=${DB_CRED_USR}
                        export DB_PASSWD=${DB_CRED_PSW}
                        export DB_NAME=sanity_db
                        export DB_DATABASE=performance_database
                        ./run-performace-tests.sh
                    popd
                '''
            }
        }
    }

    post {

        cleanup {
                sh label: 'Collect Artifacts', script: '''
                mkdir -p artifacts
                pushd scripts/performance
                    CLIENT_NODES_FILE=$PWD/client_nodes
                    CLIENT_NODE=$(head -1 "$CLIENT_NODES_FILE" | awk -F[,] '{print $1}' | cut -d'=' -f2)
                    scp -q "$CLIENT_NODE":/var/tmp/perf* $WORKSPACE/artifacts/
                popd
                '''
                script {
                    // Archive Deployment artifacts in jenkins build
                    archiveArtifacts artifacts: "artifacts/*.*", onlyIfSuccessful: false, allowEmptyArchive: true
                }
        }

        always {
            script {
                // Jenkins Summary
                clusterStatus = ""
                if ( currentBuild.currentResult == "SUCCESS" ) {
                    MESSAGE = "CORTX Performance Sanity Execution Success for the build ${build_id}"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                } else if ( currentBuild.currentResult == "FAILURE" ) {
                    manager.buildFailure()
                    MESSAGE = "CORTX Performance Sanity Execution Failed for the build ${build_id}"
                    ICON = "error.gif"
                    STATUS = "FAILURE"

                } else {
                    manager.buildUnstable()
                    MESSAGE = "CORTX Performance Sanity Execution is Unstable for the build ${build_id}"
                    ICON = "warning.gif"
                    STATUS = "UNSTABLE"
                }

                PerformaceSanityStatusHTML = "<pre>${clusterStatus}</pre>"

                manager.createSummary("${ICON}").appendText("<h3>CORTX Performance Sanity Execution ${currentBuild.currentResult} </h3><p>Please check <a href=\"${BUILD_URL}/console\">Performance Sanity Execution logs</a> for more info <h4>Cluster Status:</h4>${PerformaceSanityStatusHTML}", false, false, false, "red")

                // Email Notification
                env.build_stage = "${build_stage}"
                env.cluster_status = "${PerformaceSanityStatusHTML}"
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

    }
}
