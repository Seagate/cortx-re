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
		component = "sspl"
        branch = "custom-ci"
        os_version = "centos-7.8.2003"
        release_dir = "/mnt/bigstorage/releases/cortx"
        custom_build_number = get_custom_build_number()
		release_tag = "custom-build-$custom_build_number"
		build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/concurrent/$release_tag/cortx_iso"
    }

	options {
		timeout(time: 30, unit: 'MINUTES')
		timestamps()
        ansiColor('xterm')
	}
	
	parameters {  
        string(name: 'SSPL_URL', defaultValue: 'https://github.com/Seagate/cortx-monitor.git', description: 'Repository URL for cortx-monitor.')
		string(name: 'SSPL_BRANCH', defaultValue: 'stable', description: 'Branch for cortx-monitor.')
	}	


	stages {
		stage('Checkout') {
			steps {
				script { build_stage = env.STAGE_NAME }
				dir ('cortx-sspl') {
					checkout([$class: 'GitSCM', branches: [[name: "${SSPL_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${SSPL_URL}"]]])
				}
			}
		}
		
		stage('Install Dependencies') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: '', script: '''
					yum install sudo python-Levenshtein libtool doxygen python-pep8 openssl-devel graphviz check-devel -y
				'''
			}
		}

		stage('Build') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Build', returnStatus: true, script: '''
					set -xe
					if [ "${SSPL_BRANCH}" == "Cortx-v1.0.0_Beta" ]; then
						mv cortx-sspl sspl
						pushd sspl
					else
						pushd cortx-sspl
					fi	
					VERSION=$(cat VERSION)
					export build_number=${custom_build_number}
					#Execute build script
					echo "Executing build script"
					echo "VERSION:$VERSION"
					./jenkins/build.sh -l DEBUG
					popd
				'''	
			}
		}
		
		stage ('Upload') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Copy RPMS', script: '''
					mkdir -p $build_upload_dir
					cp /root/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir
					cp /root/rpmbuild/RPMS/noarch/*.rpm $build_upload_dir
					createrepo -v $build_upload_dir
				'''
			}
		}
	}
}