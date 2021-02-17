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
		component = "csm-agent"
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
        string(name: 'CSM_AGENT_URL', defaultValue: 'https://github.com/Seagate/cortx-management.git', description: 'Repository URL for cortx-management build.')
		string(name: 'CSM_AGENT_BRANCH', defaultValue: 'stable', description: 'Branch for cortx-management build.')
	}	


	stages {

		stage('Checkout') {
			
			steps {
				script { build_stage = env.STAGE_NAME }
				dir('cortx-manager') {
				    checkout([$class: 'GitSCM', branches: [[name: "${CSM_AGENT_BRANCH}"]], doGenerateSubmoduleConfigurations: false,  extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CSM_AGENT_URL}"]]])
				}
				dir('seagate-ldr') {
				    checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/seagate-ldr.git']]])
				}
			}
		}
		
		stage('Install Dependencies') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: '', script: """
				yum-config-manager --disable cortx-C7.7.1908,cortx-uploads
				yum-config-manager --add http://cortx-storage.colo.seagate.com/releases/cortx/github/stable/$os_version/last_successful/
				echo "gpgcheck=0" >> \$(ls /etc/yum.repos.d/cortx-storage*.repo)
				yum clean all && rm -rf /var/cache/yum
					if [ "${CSM_AGENT_BRANCH}" == "Cortx-v1.0.0_Beta" ]; then
						yum install -y eos-py-utils
						pip3.6 install  pyinstaller==3.5
					else
						yum install -y cortx-prvsnr
						pip3.6 install  pyinstaller==3.5
					fi
				"""
			}
		}	
		
		stage('Build') {
			steps {
				script { build_stage = env.STAGE_NAME }
				// Exclude return code check for csm_setup and csm_test
				sh label: 'Build', returnStatus: true, script: '''
				pushd cortx-manager
					BUILD=$(git rev-parse --short HEAD)
					VERSION=$(cat VERSION)
					echo "Executing build script"
					echo "VERSION:$VERSION"
					echo "Python:$(python --version)"
					./cicd/build.sh -v $VERSION -b $BUILD_NUMBER -t -i -n ldr -l $WORKSPACE/seagate-ldr
				popd	
				'''	
			}
		}
		
		stage ('Upload') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Copy RPMS', script: '''
					mkdir -p $build_upload_dir
					cp ./cortx-manager/dist/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir
					createrepo -v $build_upload_dir
				'''
			}
		}
	}
}