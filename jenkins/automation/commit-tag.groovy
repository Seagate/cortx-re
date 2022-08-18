#!/usr/bin/env groovy
pipeline {

    agent {
        node {
            label 'docker-image-builder-centos-7.9.2009'
        }
    }

    parameters {
        string(name: 'CORTX_IMAGE', defaultValue: '', description: 'CORTX component image')
        string(name: 'GIT_TAG', defaultValue: '', description: 'Tag Name')
        string(name: 'TAG_MESSAGE', defaultValue: '', description: 'Tag Message')
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for commit tag script', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for commit tag script', trim: true)
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
        
        stage("Tag Component Commits") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    withCredentials([usernamePassword(credentialsId: 'cortx-admin-github', passwordVariable: 'PASSWD', usernameVariable: 'USER_NAME')]) {
                        sh """ 
                            export CORTX_IMAGE="${CORTX_IMAGE}"
                            export GIT_TAG="${GIT_TAG}"
                            export TAG_MESSAGE="${TAG_MESSAGE}"
                            bash scripts/release_support/image-based-commit-tag.sh
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