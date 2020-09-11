pipeline { 
    agent {
        node {
            label 'docker-rhel-7.7.1908-base-cortex-node'
        }
    }
    
	parameters {  
        string(name: 'PRVSNR_URL', defaultValue: 'https://github.com/Seagate/cortx-prvsnr', description: 'Branch for Provisioner.')
		string(name: 'PRVSNR_BRANCH', defaultValue: 'main', description: 'Branch for Provisioner.')
	}	

	environment {
        env="dev"
		component="provisioner"
        branch="release"
        os_version="rhel-7.7.1908"
        release_dir="/mnt/bigstorage/releases/eos"
        build_upload_dir="$release_dir/components/github/custom-ci/$branch/$os_version/$env/$component/"
    }

    options {
        timeout(time: 15, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        disableConcurrentBuilds()   
    }
    
    stages {
        stage('Checkout') {
            steps {
				script { build_stage=env.STAGE_NAME }
					checkout([$class: 'GitSCM', branches: [[name: '${PRVSNR_BRANCH}']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: '${PRVSNR_URL}']]])
            }
        }

        stage('Build') {
            steps {
				script { build_stage=env.STAGE_NAME }
                sh encoding: 'utf-8', label: 'Provisioner RPMS', returnStdout: true, script: """
                    sh ./devops/rpms/buildrpm.sh -g \$(git rev-parse --short HEAD) -e 1.0.0 -b ${BUILD_NUMBER}
                """
                sh encoding: 'utf-8', label: 'Provisioner CLI RPMS', returnStdout: true, script: """
				    sh ./cli/buildrpm.sh -g \$(git rev-parse --short HEAD) -e 1.0.0 -b ${BUILD_NUMBER}
                """
				
				sh encoding: 'utf-8', label: 'Provisioner API RPMS', returnStdout: true, script: """
					bash ./devops/rpms/api/build_python_api.sh -vv --out-dir /root/rpmbuild/RPMS/x86_64/ --pkg-ver 1.0.0
				 """
            }
        }

		stage ('Upload') {
			steps {
				script { build_stage=env.STAGE_NAME }
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
				script { build_stage=env.STAGE_NAME }
				sh label: 'Tag last_successful', script: '''pushd $build_upload_dir/
                    test -d $build_upload_dir/last_successful && rm -f last_successful
                    ln -s $build_upload_dir/$BUILD_NUMBER last_successful
                    popd
				'''
			}
		}
	}
}