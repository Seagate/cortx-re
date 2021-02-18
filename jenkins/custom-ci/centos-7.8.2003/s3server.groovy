#!/usr/bin/env groovy
def get_custom_build_number() {

  def upstreamCause = currentBuild.rawBuild.getCause(Cause.UpstreamCause)
  if (upstreamCause) {

	def upstreamBuildID = Jenkins.getInstance().getItemByFullName(upstreamCause.getUpstreamProject(), hudson.model.Job).getBuildByNumber(upstreamCause.getUpstreamBuild()).getId()
	def mainupstreamCause = Jenkins.getInstance().getItemByFullName(upstreamCause.getUpstreamProject(), hudson.model.Job).getBuildByNumber(upstreamCause.getUpstreamBuild()).getCause(Cause.UpstreamCause)
	
	if (mainupstreamCause) {
		def mainupstreamBuildID = Jenkins.getInstance().getItemByFullName(mainupstreamCause.getUpstreamProject(), hudson.model.Job.class).getBuildByNumber(mainupstreamCause.getUpstreamBuild()).getId()
		return mainupstreamBuildID
	} else {
		def buildNumber = currentBuild.number
		return buildNumber
		}
  } else {
    def buildNumber = currentBuild.number
	return buildNumber
	}
}

pipeline {
	agent {
		node {
			label 'docker-io-centos-7.8.2003-node'
		}
	}
	
	options {
		timeout(time: 55, unit: 'MINUTES')
		timestamps()  
	}

	environment {
     	release_dir = "/mnt/bigstorage/releases/cortx"
		branch = "custom-ci" 
		os_version = "centos-7.8.2003"
		component = "s3server"
		custom_build_number = get_custom_build_number()
		release_tag = "custom-build-$custom_build_number"
		build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/concurrent/$release_tag/cortx_iso"
    }

	parameters {  
	    string(name: 'S3_URL', defaultValue: 'https://github.com/Seagate/cortx-s3server', description: 'Repository URL for S3Server')
        string(name: 'S3_BRANCH', defaultValue: 'stable', description: 'Branch for S3Server')
		
		choice(
            name: 'MOTR_BRANCH', 
            choices: ['custom-ci', 'stable', 'Cortx-v1.0.0_Beta'],
            description: 'Branch name to pick-up Motr components rpms'
        )

	}

	
	stages {
	
		stage ("Dev Build") {
			stages {
				
			
				stage('Checkout') {
					steps {
						step([$class: 'WsCleanup'])
						checkout([$class: 'GitSCM', branches: [[name: "${S3_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: '${S3_URL}']]])
						}
					}
				
				
				stage('Build s3server RPM') {
					steps {
						script { build_stage = env.STAGE_NAME }
								sh label: '', script: '''
								sed '/baseurl/d' /etc/yum.repos.d/motr_current_build.repo
								echo "baseurl=http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/$os_version/concurrent/$release_tag/cortx_iso/"  >> /etc/yum.repos.d/motr_current_build.repo
							    yum-config-manager --disable cortx-C7.7.1908
								yum clean all;rm -rf /var/cache/yum
								export build_number=${custom_build_numbe}
								
								if [ "${S3_BRANCH}" == "Cortx-v1.0.0_Beta" ]; then
									yum erase log4cxx_cortx-devel cortx-motr{,-devel} -y -q
									yum install eos-core{,-devel} -y
								else
									yum install cortx-motr{,-devel} -y
									yum erase log4cxx_eos-devel -q -y
								fi	
								
								./rpms/s3/buildrpm.sh -P $PWD
							'''
					}
				}
				
				stage('Build s3iamcli RPM') {
					steps {
						script { build_stage = env.STAGE_NAME }
								sh label: '', script: '''
								export build_number=${custom_build_number}
								./rpms/s3iamcli/buildrpm.sh -P $PWD
							'''
					}
				}

				stage ('Copy RPMS') {
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
    }
}