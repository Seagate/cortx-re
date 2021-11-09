pipeline {
    agent {
		node {
			label 'docker-cp-centos-7.8.2003-node'
		}
	}
	
	options {
		timeout(time: 60, unit: 'MINUTES')
        ansiColor('xterm') 
	}
	
	parameters {
        string(name: 'BUILD_FROM', defaultValue: '', description: '''Please provide full build url till RELEASE.INFO. And give proper build url here from where you want to check logs.
 <h6> e.g. http://cortx-storage.colo.seagate.com/releases/cortx/github/stable/centos-7.9.2009/105/dev/RELEASE.INFO </h6>''')
        string(name: 'BUILD_TO', defaultValue: '', description: '''Please provide full build url till RELEASE.INFO. And give proper build url here till where you want to check logs.
 <h6> e.g. http://cortx-storage.colo.seagate.com/releases/cortx/github/stable/centos-7.9.2009/110/dev/RELEASE.INFO </h6>''')
    }

    stages {	
		stage ('Checkout Script') {
			steps {
			    checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
			}
		}
		
		stage ('Build MANIFEST') {
			steps {
                withCredentials([string(credentialsId: 'shailesh-github-token', variable: 'ACCESS_TOKEN')]) {
                    sh label: 'Build MANIFEST', script: """
                        
                        sh -x scripts/release_support/changelog.sh ${BUILD_FROM} ${BUILD_TO}
                        cp /root/git_build_checkin_stats/clone/git-build-checkin-report.txt CHANGESET.txt 
                    """
                }   
			}
		}
		
	}

    post {
		always {
            script {
				archiveArtifacts artifacts: "CHANGESET.txt", onlyIfSuccessful: false, allowEmptyArchive: true
            }
        }
    }
}

