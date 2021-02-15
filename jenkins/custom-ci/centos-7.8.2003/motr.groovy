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
			label 'docker-motr-centos-7.8.2003-node'
		}
	}

	options {
		timestamps()
	}
	

	parameters {  
        string(name: 'MOTR_URL', defaultValue: 'https://github.com/Seagate/cortx-motr', description: 'Branch for Motr.')
		string(name: 'MOTR_BRANCH', defaultValue: 'stable', description: 'Branch for Motr.')
		string(name: 'S3_URL', defaultValue: 'https://github.com/Seagate/cortx-s3server', description: 'Branch for S3Server')
		string(name: 'S3_BRANCH', defaultValue: 'stable', description: 'Branch for S3Server')
		string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Branch to be used for Hare build.')
		string(name: 'HARE_BRANCH', defaultValue: 'stable', description: 'Branch to be used for Hare build.')

	}	

	environment {
     	release_dir = "/mnt/bigstorage/releases/cortx"
		os_version = "centos-7.8.2003"
		branch = "custom-ci"
		component = "motr"
		env = "dev"
		custom_build_number = get_custom_build_number()
		build_upload_dir = "$release_dir/components/github/$branch/$os_version/concurrent/$custom_build_number/$env/$component/"
    }

	stages {	
	
		stage('Checkout') {
			steps {
                step([$class: 'WsCleanup'])
				checkout([$class: 'GitSCM', branches: [[name: "$MOTR_BRANCH"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "$MOTR_URL"]]])
			}
		}
	
	
	stage('Install Dependencies') {
		    steps {
				script { build_stage = env.STAGE_NAME }
				sh label: '', script: '''
						export build_number=${BUILD_ID}
						kernel_src=$(ls -1rd /lib/modules/*/build | head -n1)
						
						
						if [ "${MOTR_BRANCH}" == "Cortx-v1.0.0_Beta" ]; then
						cp mero.spec.in mero.spec
						sed -i 's/@.*@/111/g' mero.spec
						yum-builddep -y mero.spec 
						else
						cp cortx-motr.spec.in cortx-motr.spec
						sed -i 's/@.*@/111/g' cortx-motr.spec
						yum-builddep -y cortx-motr.spec
						fi
					'''	
			}
		}

		stage('Build') {
			steps {
				script { build_stage = env.STAGE_NAME }
						sh label: '', script: '''
						rm -rf /root/rpmbuild/RPMS/x86_64/*.rpm
						KERNEL=/lib/modules/$(yum list installed kernel | tail -n1 | awk '{ print $2 }').x86_64/build
						./autogen.sh
						./configure --with-linux=$KERNEL
						export build_number=${BUILD_ID}
						make rpms
					'''
			}
		}
		
		stage ('Copy RPMS') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Copy RPMS', script: '''
					test -d /$BUILD_NUMBER && rm -rf $build_upload_dir/$BUILD_NUMBER
					mkdir -p $build_upload_dir/$BUILD_NUMBER
					cp /root/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir/$BUILD_NUMBER
				'''
			}
		}
		
		stage ('Repo Creation') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Repo Creation', script: '''pushd $build_upload_dir/$BUILD_NUMBER
					rpm -qi createrepo || yum install -y createrepo
					createrepo .
					popd
				'''
			}
		}
		
		stage ('Set Current Build') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Tag last_successful', script: '''
					pushd $build_upload_dir
					test -L $build_upload_dir/current_build && rm -f current_build
					ln -s $build_upload_dir/$BUILD_NUMBER current_build
					popd
				'''
			}
		}
	
	
		stage ("Trigger Downstream Jobs") {
			parallel {
				stage ("build S3Server") {
					steps {
						script { build_stage = env.STAGE_NAME }
						build job: 's3-custom-build', wait: true,
						parameters: [
									string(name: 'S3_BRANCH', value: "${S3_BRANCH}"),
									string(name: 'MOTR_BRANCH', value: "custom-ci"),
									string(name: 'S3_URL', value: "${S3_URL}")
								]
					}
				}

				stage ("build Hare") {
					steps {
						script { build_stage = env.STAGE_NAME }
						build job: 'hare-custom-build', wait: true,
						parameters: [
									string(name: 'HARE_BRANCH', value: "${HARE_BRANCH}"),
									string(name: 'MOTR_BRANCH', value: "custom-ci"),
									string(name: 'HARE_URL', value: "${HARE_URL}")
								]
					}
				}
			}	
		}
	
		stage ('Tag last_successful') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Tag last_successful', script: '''pushd $build_upload_dir
					test -d $build_upload_dir/last_successful && rm -f last_successful
					ln -s $build_upload_dir/$BUILD_NUMBER last_successful
					popd
				'''
			}
		}
	}	
}