#!/usr/bin/env groovy
pipeline {
	agent {
		node {
			label 'docker-centos-7.9.2009-node'
		}
	}
	
	environment {
		component = "csm-web"
        branch = "custom-ci" 
        os_version = "centos-7.9.2009"
        release_dir = "/mnt/bigstorage/releases/cortx"
		release_tag = "custom-build-$CUSTOM_CI_BUILD_ID"
		build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/$release_tag/cortx_iso"
    }

	options {
		timeout(time: 60, unit: 'MINUTES')
		timestamps ()
        ansiColor('xterm')
		buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '10'))
	}
	
	parameters {  
        string(name: 'CSM_WEB_URL', defaultValue: 'https://github.com/Seagate/cortx-management-web.git', description: 'Branch for cortx-management-web build.')
		string(name: 'CSM_WEB_BRANCH', defaultValue: 'stable', description: 'Branch for cortx-management-web build.')
		string(name: 'CUSTOM_CI_BUILD_ID', defaultValue: '0', description: 'Custom CI Build Number')
	}	


	stages {

		stage('Checkout') {
			steps {
				script { build_stage = env.STAGE_NAME }
				dir('cortx-management-web') {
				    checkout([$class: 'GitSCM', branches: [[name: "${CSM_WEB_BRANCH}"]], doGenerateSubmoduleConfigurations: false,  extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CSM_WEB_URL}"]]])
				}
				dir('seagate-ldr') {
				    checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/seagate-ldr.git']]])
				}
			}
		}
		
		stage('Install Dependencies') {
			steps {
				script { build_stage = env.STAGE_NAME }

				sh label: 'Configure yum repositories', script: """
					yum-config-manager --add-repo=http://ssc-nfs-cicd1.colo.seagate.com/releases/cortx/github/integration-custom-ci/$os_version/$release_tag/cortx_iso/
					yum-config-manager --save --setopt=ssc-nfs-cicd1*.gpgcheck=1 ssc-nfs-cicd1* && yum-config-manager --save --setopt=ssc-nfs-cicd1*.gpgcheck=0 ssc-nfs-cicd1*
					yum clean all && rm -rf /var/cache/yum
				"""

				sh label: 'Install Provisionr', script: '''
					yum install -y cortx-prvsnr
					pip3.6 install  pyinstaller==3.5
				'''
			}
		}	
		
		stage('Build') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Build', script: '''
					pushd cortx-management-web
						BUILD=$(git rev-parse --short HEAD)
						VERSION="2.0.0"
						echo "Executing build script"
						echo "VERSION:$VERSION"
						echo "Python:$(python --version)"
						./cicd/build.sh -v $VERSION -b $CUSTOM_CI_BUILD_ID -t -i -n ldr -l $WORKSPACE/seagate-ldr/ldr-brand/
					popd	
				'''	
			}
		}
		
		stage ('Upload') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Copy RPMS', script: '''
					mkdir -p $build_upload_dir
					cp ./cortx-management-web/dist/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir
				'''
			}
		}	
	}
}	
