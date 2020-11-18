pipeline {
    agent {
		node {
			label 'docker-rhel7.7-hare-github-node'
		}
	}
    
    environment {
        env = "dev"
		component = "cortx-ha"
        branch = "release"
        os_version = "rhel-7.7.1908"
        release_dir = "/mnt/bigstorage/releases/eos"
        component_dir = "$release_dir/components/github/custom-ci/$branch/$os_version/$env/$component/"
    }
	
	parameters {
		string(name: 'HA_URL', defaultValue: 'https://github.com/Seagate/cortx-ha', description: 'Branch to be used for Hare build.')
		string(name: 'HA_BRANCH', defaultValue: 'release', description: 'Branch to be used for Hare build.')
	}
	
	
	options {
		timeout(time: 35, unit: 'MINUTES')
		timestamps()
        ansiColor('xterm')
		disableConcurrentBuilds()  
	}

	stages {
		stage('Checkout') {
			steps {
				script { build_stage=env.STAGE_NAME }
				dir ('cortx-ha') {
					checkout([$class: 'GitSCM', branches: [[name: '$HA_BRANCH']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: false, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: '$HA_URL']]])
				}
			}
		}
	
		stage('Install Dependencies') {
			steps {
				script { build_stage=env.STAGE_NAME }
				sh label: '', script: '''
					pushd $component
					yum clean all;rm -rf /var/cache/yum
					yum -q erase eos-py-utils -y;yum install eos-py-utils -y
					bash jenkins/cicd/cortx-ha-dep.sh
					pip3 install numpy
					popd
				'''
			}
		}

		stage('Build') {
			steps {
				script { build_stage=env.STAGE_NAME }
				sh label: 'Build', script: '''
					set -xe
					pushd $component
					echo "Executing build script"
   				   ./jenkins/build.sh -b $BUILD_NUMBER
					popd
				'''	
			}
		}
		
		stage('Test') {
			steps {
				script { build_stage=env.STAGE_NAME }
				sh label: 'Test', script: '''
					set -xe
					pushd $component
					yum localinstall $WORKSPACE/$component/dist/rpmbuild/RPMS/x86_64/cortx-ha-*.rpm -y
					bash jenkins/cicd/cortx-ha-cicd.sh
					popd
				'''	
			}
		}

        stage ('Upload') {
			steps {
				script { build_stage=env.STAGE_NAME }
				sh label: 'Copy RPMS', script: '''
					mkdir -p $component_dir/$BUILD_NUMBER
					cp $WORKSPACE/cortx-ha/dist/rpmbuild/RPMS/x86_64/*.rpm $component_dir/$BUILD_NUMBER
				'''
                sh label: 'Repo Creation', script: '''pushd $component_dir/$BUILD_NUMBER
					rpm -qi createrepo || yum install -y createrepo
					createrepo .
					popd
				'''
			}
		}
	
		stage ('Tag last_successful') {
			steps {
				script { build_stage=env.STAGE_NAME }
				sh label: 'Tag last_successful', script: '''pushd $component_dir/
					test -L $component_dir/last_successful && rm -f last_successful
					ln -s $component_dir/$BUILD_NUMBER last_successful
					popd
				'''
			}
		}
	}
}