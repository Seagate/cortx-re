#!/usr/bin/env groovy

pipeline {
    agent {
        node {
            label 'automation-node-centos'
        }
    }
    
    options {
        timeout(time: 15, unit: 'MINUTES')
        timestamps() 
    }

    parameters {
        string(name: 'GIT_TAG', defaultValue: '', description: 'Git Tag Name')
        string(name: 'RELEASE_INFO', defaultValue: '', description: 'Release Info')
    }

    stages {

        stage('Checkout Script') {
            steps {             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: '*/poc_git_tag']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/gowthamchinna/cortx-re']]])                
                }
            }
        }

        stage('Generate Report') {
            steps {             
                script {

                    withCredentials([usernamePassword(credentialsId: 'cortx-admin-github', passwordVariable: 'PASSWD', usernameVariable: 'USER_NAME')]) {

                        sh """ bash scripts/release_support/git_tag.sh --tag "${GIT_TAG}" --release "${RELEASE_INFO}" --cred "${PASSWD}"  """
                    
                    }      
                }
            }
        }
    }
}	