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
        booleanParam(name: 'DEBUG', defaultValue: false, description: 'Select this if you want to Delete the provided Tag')
    }

    stages {
        stage('Checkout Script') {
            steps {
                script { build_stage=env.STAGE_NAME }             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])                
                }
            }
        }
        
        stage("Tag Component Commits") {
            steps {
                script { build_stage=env.STAGE_NAME }
                script {
                    sh """ bash scripts/release_support/image-based-commit-tag.sh """			
                }
            }
	    }
    }    
}