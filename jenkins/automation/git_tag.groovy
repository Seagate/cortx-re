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
        string(name: 'RELEASE_INFO', defaultValue: '', description: 'Release Info')
        string(name: 'GIT_TAG', defaultValue: '', description: 'Git Tag Name')
        string(name: 'GIT_TAG_DESCRIPTION', defaultValue: '', description: 'Git Tag Description')
    }

    stages {

        stage('Checkout Script') {
            steps {             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])                
                }
            }
        }

        stage('Generate Report') {
            steps {             
                script {

                    withCredentials([usernamePassword(credentialsId: 'cortx-admin-github', passwordVariable: 'PASSWD', usernameVariable: 'USER_NAME')]) {

                        sh """ bash scripts/release_support/git_tag.sh --tag "${GIT_TAG}" --release "${RELEASE_INFO}" --cred "${PASSWD}" --message "${GIT_TAG_DESCRIPTION}"  """
                    }      
                }
            }
        }
    }
}	