#!/usr/bin/env groovy
pipeline {
	agent {
		node {
			label 'docker-cp-centos-7.8.2003-node'
		}
	}
	
	environment { 
		version = "2.0.0"     
        env = "dev"
		component = "csm-agent"
        branch = "main"
        os_version = "centos-7.8.2003"
        release_dir = "/mnt/bigstorage/releases/cortx"
        build_upload_dir = "$release_dir/components/github/$branch/$os_version/$env/$component"
    }

	options {
		timeout(time: 60, unit: 'MINUTES')
		timestamps ()
        ansiColor('xterm')
        disableConcurrentBuilds()
	}

	parameters {  
        string(name: 'CSM_AGENT_URL', defaultValue: 'https://github.com/Seagate/cortx-management.git', description: 'Repository URL for cortx-management build.')
		string(name: 'CSM_AGENT_BRANCH', defaultValue: 'stable', description: 'Branch for cortx-management build.')
	}	


	stages {

		stage('Checkout') {
			steps {
				script { build_stage = env.STAGE_NAME }

				checkout([$class: 'GitSCM', branches: [[name: "${CSM_AGENT_BRANCH}"]], doGenerateSubmoduleConfigurations: false,  extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CSM_AGENT_URL}"]]])
			}
		}
		
		stage('Install Dependencies') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: '', script: '''
				# Install cortx-prereq package
					pip3 uninstall pip -y && yum install python3-pip -y && ln -s /usr/bin/pip3 /usr/local/bin/pip3
					curl -s http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/rpm/install-cortx-prereq.sh | bash 

					yum erase python36-PyYAML -y 
					pip3.6 install  pyinstaller==3.5
				'''
			}
		}	
		
		stage('Build') {
			steps {
				script { build_stage = env.STAGE_NAME }
				// Exclude return code check for csm_setup and csm_test
				sh label: 'Build', script: '''
					BUILD=$(git rev-parse --short HEAD)
					echo "Executing build script"
					echo "Python:$(python --version)"
					./cicd/build.sh -v $version -b $BUILD_NUMBER -t -i
				'''	
			}
		}

	}
	
	post {
		always {
			script {
            	
				echo 'Cleanup Workspace.'
				deleteDir() /* clean up our workspace */

				env.release_build = (env.release_build != null) ? env.release_build : "" 
				env.release_build_location = (env.release_build_location != null) ? env.release_build_location : ""
				env.component = (env.component).toUpperCase()
				env.build_stage = "${build_stage}"

				def toEmail = ""
				def recipientProvidersClass = [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
				if( manager.build.result.toString() == "FAILURE" ) {
					toEmail = "shailesh.vaidya@seagate.com"
					recipientProvidersClass = [[$class: 'DevelopersRecipientProvider'],[$class: 'RequesterRecipientProvider']]
				}

				emailext (
					body: '''${SCRIPT, template="component-email.template"}''',
					mimeType: 'text/html',
				    subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
					attachLog: true,
					to: toEmail,
					recipientProviders: recipientProvidersClass
				)
			}
		}
    }
}