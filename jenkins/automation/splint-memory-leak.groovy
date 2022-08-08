
pipeline {
    agent {
        node {
            label "docker-centos-7.9.2009-node"
        }
    }

    options {
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    parameters {
        string(name: 'CORTX_MOTR_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Memory Leak Analysis', trim: true)
        string(name: 'CORTX_MOTR_REPO', defaultValue: 'https://github.com/Seagate/cortx-motr/', description: 'Repository for Memory Leak Analysis', trim: true)
    }

    stages {
        stage('Checkout Script') {
            steps {
                cleanWs()
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_MOTR_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_MOTR_REPO}"]]])
                }
            }
        }

        stage ('Execute Memory Analysis') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: '', returnStatus: true, script: '''
                yum install splint -y
                mkdir -p artifacts
                for file in  $(find $PWD -type f -regextype posix-egrep -regex ".+\\\\.(c|h|lcl)$"); do splint +trytorecover -preproc -warnposix $file >> artifacts/split-analysis.log ; done || echo "Log Generated"
                '''
            }
        }
    }

    post {

        cleanup {
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
                    MESSAGE = "Memory Leak Analysis Execution Success for the build ${build_id}"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                } else if ( currentBuild.currentResult == "FAILURE" ) {
                    manager.buildFailure()
                    MESSAGE = "Memory Leak Analysis Execution Failed for the build ${build_id}"
                    ICON = "error.gif"
                    STATUS = "FAILURE"

                } else {
                    manager.buildUnstable()
                    MESSAGE = "Memory Leak Analysis Execution is Unstable for the build ${build_id}"
                    ICON = "warning.gif"
                    STATUS = "UNSTABLE"
                }

                MemoryLeakAnalysisStatusHTML = "<pre>${clusterStatus}</pre>"

                manager.createSummary("${ICON}").appendText("<h3>Memory Leak Analysis Execution ${currentBuild.currentResult} </h3><p>Please check <a href=\"${BUILD_URL}/artifact/artifacts/split-analysis.log\">Memory Leak Analysis logs</a> for Memory leak errors</p><p>Please check <a href=\"${BUILD_URL}/console\">Memory Leak Analysis Execution logs</a> for more info</p>", false, false, false, "red")

                // Email Notification
                env.build_stage = "${build_stage}"
                env.cluster_status = "${MemoryLeakAnalysisStatusHTML}"
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