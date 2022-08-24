
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
        string(name: 'MOTR_REPO_URL', defaultValue: 'https://github.com/Seagate/cortx-motr', description: 'Repository for Memory Leak Analysis', trim: true)
        string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Memory Leak Analysis', trim: true)
    }

    environment {
        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        REPO_URL = "${ghprbGhRepository != null ? GPR_REPO : MOTR_REPO_URL}"
        BRANCH = "${sha1 != null ? sha1 : MOTR_BRANCH}"
        PR_REFSEPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        API_REPO = "gauravchaudhari02/cortx-motr"
        GH_TOKEN = credentials('cortx-admin-token')
    }

    stages {
        stage('Checkout Script') {
            steps {
                cleanWs()
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${REPO_URL}",  name: 'origin', refspec: "${PR_REFSEPEC}"]]])
                }
            }
        }

        stage ('Execute Memory Analysis') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Run sp lint on files', script: """
                    yum install splint -y
                    mkdir artifacts
                    files=""
                    if [[ ! -z ${ghprbPullLink} ]]; then
                        files=\$(curl -sH \"Accept: application/vnd.github+json\" -H \"Authorization: token ${GH_TOKEN}\" \"https://api.github.com/repos/${API_REPO}/pulls/${ghprbPullId}/files\" | jq '.[].filename' | tr -d '\"' | grep -e '.*.c\$' -e '.*.h\$' -e '.*.lcl\$' || echo '')
                    else
                        files=\$(find $PWD -type f -regextype posix-egrep -regex '.+\\\\.(c|h|lcl)\$')
                    fi    
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