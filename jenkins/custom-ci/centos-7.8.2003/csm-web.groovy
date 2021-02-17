#!/usr/bin/env groovy
def get_custom_build_number() {

  def upstreamCause = currentBuild.rawBuild.getCause(Cause.UpstreamCause)
  if (upstreamCause) {
	def upstreamBuildID = Jenkins.getInstance().getItemByFullName(upstreamCause.getUpstreamProject(), hudson.model.Job.class).getBuildByNumber(upstreamCause.getUpstreamBuild()).getId()
	return upstreamBuildID
  } else {
    def buildNumber = currentBuild.number
	return buildNumber
	}
}

pipeline {
	agent {
		node {
			label 'docker-cp-centos-7.8.2003-node'
		}
	}
	
	environment {
		component = "csm-web"
        branch = "custom-ci" 
        os_version = "centos-7.8.2003"
        release_dir = "/mnt/bigstorage/releases/cortx"
        custom_build_number = get_custom_build_number()
		release_tag = "custom-build-$custom_build_number"
		build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/concurrent/$release_tag/cortx_iso"
    }

	options {
		timeout(time: 60, unit: 'MINUTES')
		timestamps ()
        ansiColor('xterm')
	}
	
	parameters {  
        string(name: 'CSM_WEB_URL', defaultValue: 'https://github.com/Seagate/cortx-management-web.git', description: 'Branch for cortx-management-web build.')
		string(name: 'CSM_WEB_BRANCH', defaultValue: 'stable', description: 'Branch for cortx-management-web build.')
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
				sh label: '', script: '''
					yum install -y cortx-py-utils cortx-prvsnr
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
						VERSION=$(cat VERSION)
						echo "Executing build script"
						echo "VERSION:$VERSION"
						echo "Python:$(python --version)"
						./cicd/build.sh -v $VERSION -b $custom_build_number -t -i -n ldr -l $WORKSPACE/seagate-ldr/ldr-brand/
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
					createrepo -v $build_upload_dir
				'''
			}
		}	

	}
}	
