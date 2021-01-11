pipeline {

	agent {
		node {
			label 'Test-node-ssc-vm-c-456'
		}
	}
	 triggers {
         cron('0 */6 * * *')
    }
	environment {
		COMMIT_HASH_CORTX_CSM_AGENT=""
		COMMIT_HASH_CORTX_CSM_WEB=""
		COMMIT_HASH_CORTX_HARE=""
		COMMIT_HASH_CORTX_HA=""
		COMMIT_HASH_CORTX_MOTR=""
		COMMIT_HASH_CORTX_PRVSNR=""
		COMMIT_HASH_CORTX_S3SERVER=""
		COMMIT_HASH_CORTX_SSPL=""
	}	
	
	parameters {
		string(name: 'RELEASE_INFO_URL', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx_builds/centos-7.8.2003/552/RELEASE.INFO', description: 'RELEASE BUILD')
	}
	stages {
		
		stage ('commit hash') {
			steps {
				sh label: 'commit hash', script: '''#!/bin/bash
				RELEASE_INFO=$RELEASE_INFO_URL
				GITHUB_ORG="Seagate"
				declare -A COMPONENT_LIST=( 
				[cortx-s3server]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-s3server.git"
				[cortx-motr]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-motr.git"
				[cortx-hare]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-hare.git"
				[cortx-ha]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-ha.git"
				[cortx-prvsnr]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-prvsnr.git"
				[cortx-sspl]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-monitor.git"
				[cortx-csm_agent]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-manager.git"
				[cortx-csm_web]="https://${GIT_CRED}@github.com/${GITHUB_ORG}/cortx-management-portal.git"
				#[cortx-fs]="https://$GIT_CRED@github.com/$GITHUB_ORG/cortx-posix.git"
			)
				git config --global user.email "cortx-re@seagate.com"
				git config --global user.name "cortx-re"
				wget -q "$RELEASE_INFO" -O RELEASE.INFO
				for component in "${!COMPONENT_LIST[@]}"
					do
						dir=$(echo "${COMPONENT_LIST[$component]}" |  awk -F'/' '{print $NF}')
						git clone --quiet "${COMPONENT_LIST[$component]}" "$dir" > /dev/null
						rc=$?
						if [ $rc -ne 0 ]; then 
							echo "ERROR:git clone failed for $component"
							exit 1
						fi
						if [ "$component" == cortx-hare ]; then
							COMMITT_HASH_CORTX_HARE=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | cut -d. -f1 |  sed 's/git//g');
							COMMIT_HASH_CORTX_HARE=sh(script: 'echo ${COMMITT_HASH_CORTX_HARE}', returnStdout: true).trim()
						elif [ "$component" == cortx-sspl ]; then
							COMMITT_HASH_CORTX_SSPL=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | cut -d. -f1 |  sed 's/git//g');
							COMMIT_HASH_CORTX_SSPL=sh(script: 'echo ${COMMITT_HASH_CORTX_HARE}', returnStdout: true).trim()
						elif [ "$component" == cortx-ha ]; then
							COMMITT_HASH_CORTX_HA=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | cut -d. -f1 |  sed 's/git//g');
							COMMIT_HASH_CORTX_HA=sh(script: 'echo ${COMMITT_HASH_CORTX_HA}', returnStdout: true).trim()
						elif [ "$component" == "cortx-csm_agent" ]; then
							COMMITT_HASH_CORTX_CSM_AGENT=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $3}' |  cut -d. -f1);
							COMMIT_HASH_CORTX_CSM_AGENT=sh(script: 'echo ${COMMITT_HASH_CORTX_CSM_AGENT}', returnStdout: true).trim()
						elif [ "$component" == "cortx-csm_web" ]; then
							COMMITT_HASH_CORTX_CSM_WEB=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $3}' |  cut -d. -f1);
							COMMIT_HASH_CORTX_CSM_WEB=sh(script: 'echo ${COMMITT_HASH_CORTX_CSM_WEB}', returnStdout: true).trim()
						elif [ "$component" == "cortx-s3server" ]; then
							COMMITT_HASH_CORTX_S3SERVER=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | sed 's/git//g');
							COMMIT_HASH_CORTX_S3SERVER=sh(script: 'echo ${COMMITT_HASH_CORTX_S3SERVER}', returnStdout: true).trim()
						elif [ "$component" == "cortx-motr" ]; then
							COMMITT_HASH_CORTX_MOTR=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | sed 's/git//g');
							COMMIT_HASH_CORTX_MOTR=sh(script: 'echo ${COMMITT_HASH_CORTX_MOTR}', returnStdout: true).trim()
						elif [ "$component" == "cortx-prvsnr" ]; then 
							COMMITT_HASH_CORTX_PRVSNR=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | sed 's/git//g');
							COMMIT_HASH_CORTX_PRVSNR=sh(script: 'echo ${COMMITT_HASH_CORTX_PRVSNR}', returnStdout: true).trim()
						else
							COMMIT_HASH=echo "Component Not Matching"
						fi
					done	
						echo "Component: $component , Repo:  ${COMPONENT_LIST[$component]}, Commit Hash: ${COMMIT_HASH_CORTX_HARE}"
						echo "Component: $component , Repo:  ${COMPONENT_LIST[$component]}, Commit Hash: ${COMMIT_HASH_CORTX_SSPL}"
						echo "Component: $component , Repo:  ${COMPONENT_LIST[$component]}, Commit Hash: ${COMMIT_HASH_CORTX_HA}"
						echo "Component: $component , Repo:  ${COMPONENT_LIST[$component]}, Commit Hash: ${COMMIT_HASH_CORTX_CSM_AGENT}"
						echo "Component: $component , Repo:  ${COMPONENT_LIST[$component]}, Commit Hash: ${COMMIT_HASH_CORTX_CSM_WEB}"
						echo "Component: $component , Repo:  ${COMPONENT_LIST[$component]}, Commit Hash: ${COMMIT_HASH_CORTX_S3SERVER}"
						echo "Component: $component , Repo:  ${COMPONENT_LIST[$component]}, Commit Hash: ${COMMIT_HASH_CORTX_MOTR}"
						echo "Component: $component , Repo:  ${COMPONENT_LIST[$component]}, Commit Hash: ${COMMIT_HASH_CORTX_PRVSNR}"
				'''	
			}		
		}
		stage ("Build custom-ci") {
					steps {
						sh label: 'commit hash', script: '''#!/bin/bash
						echo "${COMMIT_HASH_CORTX_CSM_AGENT}"
					'''
				}
		}	
	}		
}	
