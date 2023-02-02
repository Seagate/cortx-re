#!/usr/bin/env groovy
pipeline {
    agent {
		node {
			label 'docker-centos-7.9.2009-node'
		}
	}

	parameters {  
	   	string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare/', description: 'Repository URL for Hare build')
            	string(name: 'CORTX_UTILS_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Utils', trim: true)
	  	string(name: 'CORTX_UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'CORTX Utils Repository URL', trim: true)
		string(name: 'THIRD_PARTY_PYTHON_VERSION', defaultValue: 'custom', description: 'Third Party Python Version to use', trim: true)
	        string(name: 'HARE_BRANCH', defaultValue: 'stable', description: 'Branch for Hare build')
		string(name: 'CUSTOM_CI_BUILD_ID', defaultValue: '0', description: 'Custom CI Build Number')
		choice(
        		name: 'MOTR_BRANCH', 
            		choices: ['custom-ci', 'stable', 'Cortx-v1.0.0_Beta'],
           	 	description: 'Branch name to pick-up other components rpms'
        	)
	}
	

   	environment {
     	release_dir = "/mnt/bigstorage/releases/cortx"
		branch = "custom-ci"
		os_version = "centos-7.9.2009"
		component = "hare"
		release_tag = "custom-build-$CUSTOM_CI_BUILD_ID"
		build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/$release_tag/cortx_iso"
		python_deps = "${THIRD_PARTY_PYTHON_VERSION == 'cortx-2.0' ? "python-packages-2.0.0-latest" : THIRD_PARTY_PYTHON_VERSION == 'custom' ?  "python-packages-2.0.0-custom" : "python-packages-2.0.0-stable"}"
    }
	
	
	
	options {
		timeout(time: 35, unit: 'MINUTES')
		timestamps()
		buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '10'))
	}

	stages {
	
		stage('Checkout hare') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh 'mkdir -p hare'
				dir ('hare') {
					checkout([$class: 'GitSCM', branches: [[name: "${HARE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false, timeout: 15], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false, timeout: 15]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${HARE_URL}"]]])
				}
			}
		}
	
		stage('Install Dependencies') {
			steps {
				script { build_stage = env.STAGE_NAME }

				sh label: 'Install cortx-prereq', script: '''
					CORTX_UTILS_REPO_OWNER=$(echo $CORTX_UTILS_URL | cut -d "/" -f4)
					yum erase python36-PyYAML -y
					cat <<EOF >>/etc/pip.conf
[global]
timeout: 60
index-url: http://ssc-nfs-cicd1.colo.seagate.com/releases/cortx/third-party-deps/python-deps/$python_deps/
trusted-host: ssc-nfs-cicd1.colo.seagate.com
EOF
					pip3 install -r https://raw.githubusercontent.com/$CORTX_UTILS_REPO_OWNER/cortx-utils/$CORTX_UTILS_BRANCH/py-utils/python_requirements.txt
					pip3 install -r https://raw.githubusercontent.com/$CORTX_UTILS_REPO_OWNER/cortx-utils/$CORTX_UTILS_BRANCH/py-utils/python_requirements.ext.txt
					rm -rf /etc/pip.conf
					
                '''
				sh label: 'Configure yum repositories', script: '''
					set +x
					yum-config-manager --add-repo=http://ssc-nfs-cicd1.colo.seagate.com/releases/cortx/github/integration-custom-ci/$os_version/$release_tag/cortx_iso/
					yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*
					yum clean all;rm -rf /var/cache/yum
				'''	
				sh label: 'Install packages', script: '''	
					yum install cortx-py-utils cortx-motr{,-devel} -y
				'''
			}
		}

		stage('Build') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Build', returnStatus: true, script: '''
					set -xe
					pushd $component
					echo "Executing build script"
					export build_number=${CUSTOM_CI_BUILD_ID}
					make rpm
					popd
				'''	
			}
		}

        stage ('Copy RPMS') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Copy RPMS', script: '''
					mkdir -p $build_upload_dir
					cp /root/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir
				'''
			}
		}

	}
}
