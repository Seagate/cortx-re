#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
           label 'cortx-prereq-validation'
        }
    }
	
    environment {
		version = "2.0.0"
        env = "dev"
		component = "cortx-prereq"
        branch = "main"
        os_version = "centos-7.8.2003"
        release_dir = "/mnt/bigstorage/releases/cortx"
        build_upload_dir = "$release_dir/components/github/$branch/$os_version/$env/$component"
    }

    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        disableConcurrentBuilds()   
    }
    
    stages {
	
		stage('Prerequisite') {
            steps {
                sh encoding: 'utf-8', label: 'Install Prerequisite Packages', script: """
					yum install rpm-build rpmdevtools -y 
					rpmdev-setuptree		
                """
			}
		}
	
        stage('Checkout') {
            steps {
				script { build_stage = env.STAGE_NAME }
                checkout([$class: 'GitSCM', branches: [[name: 'third-party-debug']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/shailesh-vaidya/cortx-re']]])
            }
        }

        stage('Build') {
            steps {
				script { build_stage = env.STAGE_NAME }
                sh encoding: 'utf-8', label: 'Build cortx-prereq package', script: """
                    pushd ./scripts/third-party-rpm
                        ./build-prerequisite-rpm.sh -v $version -r ${BUILD_NUMBER} -g \$(git rev-parse --short HEAD)
                    popd    
                """
            }
        }

        stage('Test') {
            steps {
				script { build_stage = env.STAGE_NAME }
                sh encoding: 'utf-8', label: 'Test cortx-prereq package', script: """
                pushd ./scripts/third-party-rpm
                      ./install-cortx-prereq.sh  -t centos-7.8.2003-2.0.0-latest -p python-packages-2.0.0-latest -o centos-7.8.2003 -b main -r local
                popd  
                """
            }
        }

        stage ('Upload') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Copy RPMS', script: '''
                    mkdir -p $build_upload_dir/$BUILD_NUMBER
                    cp /root/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir/$BUILD_NUMBER
				'''
                sh label: 'Repo Creation', script: '''pushd $build_upload_dir/$BUILD_NUMBER
                    rpm -qi createrepo || yum install -y createrepo
                    createrepo .
                    popd
				'''
			}
		}
			
		stage ('Tag last_successful') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Clean-up', script: '''
                    pushd $build_upload_dir/
                    test -d $build_upload_dir/last_successful && rm -f last_successful
                    ln -s $build_upload_dir/$BUILD_NUMBER last_successful
                    popd
				'''
			}
		}
	}
	
	post {
	
		success {
				sh label: 'Clean-up', script: '''
				set +x
				rm -rf /etc/yum.repos.d/cortx-storage.colo.seagate.com* /etc/pip.conf /root/rpmbuild/RPMS/x86_64/*.rpm
                pip3 uninstall -r /opt/seagate/cortx/python-deps/python-requirements.txt -y
				yum erase cortx-prereq -y
				'''
		}
    }
}