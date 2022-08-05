#!/usr/bin/env groovy
pipeline {

    agent {
        node {
            label 'docker-image-builder-centos-7.9.2009'
        }
    }

    parameters {
        string(name: 'GIT_TAG', defaultValue: '', description: 'Image tag/Commit tag required for GitHub Release')
        string(name: 'SERVICES_VERSION', defaultValue: '', description: 'Services(cortx-k8s) version on which image deployment is tested')
        string(name: 'CHANGESET_URL', defaultValue: '', description: 'CHNAGESET.md file url.')
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for GitHub Release script', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for GitHub release script', trim: true)
    }

    stages {
        stage('Checkout Script') {
            steps {
                script { build_stage = env.STAGE_NAME }             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])
                }
            }
        }
        
        stage("Create GitHub Release") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    withCredentials([string(credentialsId: 'gaurav-github-token', variable: 'GITHUB_ACCESS_TOKEN')]) {
                        sh """
                            bash scripts/release_support/github-release.sh -t "$GIT_TAG" -v "$SERVICES_VERSION" -c "$CHANGESET_URL"
                        """
                    }			
                }
            }
	    }
    }
    post {
        always {
            cleanWs()
        }
    }            
}