#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            label 'docker-cp-centos-7.8.2003-node'
        }
    }
	
	environment {
        version = "2.0.0"
    }

    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        disableConcurrentBuilds()   
    }
    
    stages {
        stage('Checkout') {
            steps {
				script { build_stage = env.STAGE_NAME }
					checkout([$class: 'GitSCM', branches: [[name: "thrid-party-rpm"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/shailesh-vaidya/cortx-re']]])
            }
        }

        stage('Build') {
            steps {
				script { build_stage = env.STAGE_NAME }
                sh encoding: 'utf-8', label: 'Provisioner RPMS', script: """
                    pushd ./scripts/third-party-rpm
                        ./build-prerequisite-rpm.sh -v $version -r ${BUILD_NUMBER} -g \$(git rev-parse --short HEAD)
                """
            }
        }
	}
}