pipeline { 
    agent {
        node {
            label "docker-${OS_VERSION}-node"
        }
    }
    options { 
        skipDefaultCheckout()
        timeout(time: 180, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        ansiColor('xterm')
    }
    parameters {  
        string(name: 'MOTR_REPO', defaultValue: 'https://github.com/Seagate/cortx-motr', description: 'Repo for Motr')
        string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch for Motr')
    }
    environment {
        // Motr Repo Info
        MOTR_REPO = "${ghprbAuthorRepoGitUrl != null ? ghprbAuthorRepoGitUrl : MOTR_REPO}"
        MOTR_BRANCH = "${ghprbSourceBranch != null ? ghprbSourceBranch : MOTR_BRANCH}"
        MOTR_GPR_REFSEPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        MOTR_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        MOTR_PR_REFSEPEC = "${ghprbPullId != null ? MOTR_GPR_REFSEPEC : MOTR_BRANCH_REFSEPEC}"

        //////////////////////////////// BUILD VARS //////////////////////////////////////////////////
        // OS_VERSION and COMPONENTS_BRANCH are manually created parameters in jenkins job.
        
        COMPONENT_NAME = "motr".trim()
        HARE_BRANCH = "main"
        HARE_REPO = "https://github.com/Seagate/cortx-hare"
    }
    stages {
        // Build motr fromm PR source code
        stage('Build Variables Info') {
            steps {
                script { build_stage = env.STAGE_NAME }
                 sh """
                 echo ${ghprbGhRepository} && echo ${ghprbAuthorRepoGitUrl}
                    set +x
                    echo "--------------BUILD PARAMETERS -------------------"
                    echo "MOTR_REPO              = ${MOTR_REPO}"
                    echo "MOTR_BRANCH            = ${MOTR_BRANCH}"
                    echo "MOTR_PR_REFSEPEC       = ${MOTR_PR_REFSEPEC}"
                    echo "-----------------------------------------------------------"
                """ 
            }
        }
        // Run DTM-Integration-Test
        stage ("DTM-Integration Test") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        def dtmTest = build job: '/Motr/DTM-Integration-Test', wait: true,
                            parameters: [
                                string(name: 'MOTR_REPO', value: "${MOTR_REPO}"),
                                string(name: 'MOTR_BRANCH', value: "${MOTR_BRANCH}"),
                                string(name: 'HARE_BRANCH', value: "${HARE_BRANCH}"),
                                string(name: 'HARE_REPO', value: "${HARE_REPO}")
                            ]
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "DTM-Integration-Test failed"
                    }
                }
            }
        }
    }
}