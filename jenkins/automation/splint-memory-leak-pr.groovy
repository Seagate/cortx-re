
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

    environment {
        CORTX_MOTR_REPO = "https://github.com/${ghprbGhRepository}"
        CORTX_MOTR_BRANCH = "${sha1}"
        MOTR_PR_REFSEPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        GH_TOKEN = credentials('cortx-admin-token')
    }

    stages {
        stage('Checkout Script') {
            steps {
                cleanWs()
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_MOTR_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_MOTR_REPO}",  name: 'origin', refspec: "${MOTR_PR_REFSEPEC}"]]])
                }
            }
        }

        stage ('Execute Memory Analysis') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Run sp lint on files', script: """
                    yum install splint -y
                    mkdir artifacts
                    files=\$(curl -sH \"Accept: application/vnd.github+json\" -H \"Authorization: token ${GH_TOKEN}\" \"https://api.github.com/repos/Seagate/cortx-motr/pulls/${ghprbPullId}/files\" | jq '.[].filename' | tr -d '\"' | grep -e '.*.c\$' -e '.*.h\$' -e '.*.lcl\$' || echo '')
                    if [[ -z \$files ]]; then
                        echo \"INFO:No c/h/lcl files present in pull request #\${ghprbPullId} for memory leak scan\" | tee artifacts/splint-analysis.log
                        exit 0
                    else
                        for file in \$files; do splint +trytorecover -preproc -warnposix +line-len 100000 \$file | sed \'H;1h;\$!d;g;s/\\n  */ /g\' >> artifacts/splint-analysis.log; done || { echo \"ERROR: Splint execution failed. please check logs for more details\"; exit 1; }
                    fi
                """
            }
        }
    }

    post {
        always {
            script {
                // Archive Deployment artifacts in jenkins build
                archiveArtifacts artifacts: "artifacts/*.*", onlyIfSuccessful: false, allowEmptyArchive: true
            }
        }

        cleanup {
            script {
                cleanWs()
            }
        }
    }
}