#!/usr/bin/env groovy

pipeline { 
    agent {
        node {
            label "docker-centos-7.9.2009-node"
        }
    }
	
    parameters {
        string(name: 'SOURCE_BRANCH', defaultValue: 'kubernetes', description: 'Source Branch',  trim: true)
        string(name: 'TARGET_BRANCH', defaultValue: 'main', description: 'Target Branch',  trim: true)
    }

    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        buildDiscarder(logRotator(numToKeepStr: "30"))
    }

    stages {
        stage ('Checkout Script') {
            steps {
                script {

                    // Clone cortx-re repo
                    checkout([$class: 'GitSCM', branches: [[name: '*/git-diff']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/gauravchaudhari02/cortx-re']]])
                }
            }
        }

        stage ('Extract Commit Difference') {
            steps{
                script{
                    sh label: 'Execute Diff Script', script: """
                        sh -x scripts/automation/get_branch_diff.sh ${SOURCE_BRANCH} ${TARGET_BRANCH}
                        cp /root/git_branch_diff//git-branch-diff-report.txt CHANGESET.txt 
                    """
                }
            }
        }
    }
    post {
		always {
            script {
				archiveArtifacts artifacts: "CHANGESET.txt", onlyIfSuccessful: false, allowEmptyArchive: true
            }
        }
    }
}