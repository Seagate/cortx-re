pipeline {
	agent {
		node {
			label 'docker-cp-centos-7.8.2003-node'
		}
	}
	
	environment {      
        env = "dev"
		component = "csm-agent"
        branch = "custom-ci" 
        os_version = "centos-7.8.2003"
        release_dir = "/mnt/bigstorage/releases/cortx"
        build_upload_dir = "$release_dir/components/github/$branch/$os_version/$env/$component/"
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
				yum-config-manager --disable cortx-R7.7.1908
				yum-config-manager --add http://cortx-storage.colo.seagate.com/releases/cortx/github/cortx-1.0/$os_version/last_successful/
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
					mkdir -p $build_upload_dir/$BUILD_NUMBER
					cp ./cortx-manager/dist/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir/$BUILD_NUMBER
				'''
				sh label: 'Repo Creation', script: '''pushd $build_upload_dir/$BUILD_NUMBER
					rpm -qi createrepo || yum install -y createrepo
					createrepo .
					popd
				'''
				sh label: 'Tag last_successful', script: '''pushd $build_upload_dir/
					test -d $build_upload_dir/last_successful && rm -f last_successful
					ln -s $build_upload_dir/$BUILD_NUMBER last_successful
					popd
				'''
			}
		}

		stage ('Tag last_successful') {
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Tag last_successful', script: '''pushd $build_upload_dir/
					test -L $build_upload_dir/last_successful && rm -f last_successful
					ln -s $build_upload_dir/$BUILD_NUMBER last_successful
					popd
				'''
			}
		}
	}
}	