pipeline {

	agent {
		node {
			label 'Test-node-ssc-vm-c-456'
		}
	}
	 triggers {
         cron('0 */6 * * *')
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
						if [ "$component" == cortx-hare ] || [ "$component" == cortx-sspl ] || [ "$component" == cortx-ha ] || [ "$component" == cortx-fs ]; then
							COMMIT_HASH=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | cut -d. -f1 |  sed 's/git//g');
						elif [ "$component" == "cortx-csm_agent" ] || [ "$component" == "cortx-csm_web" ]; then
							COMMIT_HASH=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $3}' |  cut -d. -f1);
						else
							COMMIT_HASH=$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | sed 's/git//g');
						fi
						echo "Component: $component , Repo:  ${COMPONENT_LIST[$component]}, Commit Hash: ${COMMIT_HASH}"
					done
				'''	
			}		
		}	
		stage ("Build custom-ci") {
					steps {
						script { build_stage=env.STAGE_NAME }
						build job: 'custom-ci-build', wait: true,
						parameters: [
								string(name: 'CSM_AGENT_BRANCH', defaultValue: '${COMMIT_HASH}', description: 'Branch for CSM Agent')
								string(name: 'CSM_AGENT_URL', defaultValue: 'https://github.com/Seagate/cortx-manager', description: 'CSM_AGENT URL')
								string(name: 'CSM_WEB_BRANCH', defaultValue: '${COMMIT_HASH}', description: 'Branch for CSM Web')
								string(name: 'CSM_WEB_URL', defaultValue: 'https://github.com/Seagate/cortx-management-portal', description: 'CSM WEB URL')
								string(name: 'HARE_BRANCH', defaultValue: '${COMMIT_HASH}', description: 'Branch for Hare')
								string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Hare URL')
								string(name: 'HA_BRANCH', defaultValue: '${COMMIT_HASH}', description: 'Branch for Cortx-HA')
								string(name: 'HA_URL', defaultValue: 'https://github.com/Seagate/cortx-ha.git', description: 'Cortx-HA URL')
								string(name: 'MOTR_BRANCH', defaultValue: '${COMMIT_HASH}', description: 'Branch for Motr')
								string(name: 'MOTR_URL', defaultValue: 'https://github.com/Seagate/cortx-motr.git', description: 'Motr URL')
								string(name: 'PRVSNR_BRANCH', defaultValue: '${COMMIT_HASH}', description: 'Branch for Provisioner')
								string(name: 'PRVSNR_URL', defaultValue: 'https://github.com/Seagate/cortx-prvsnr.git', description: 'Provisioner URL')
								string(name: 'S3_BRANCH', defaultValue: '${COMMIT_HASH}', description: 'Branch for S3Server')
								string(name: 'S3_URL', defaultValue: 'https://github.com/Seagate/cortx-s3server.git', description: 'S3Server URL')
								string(name: 'SSPL_BRANCH', defaultValue: '${COMMIT_HASH}', description: 'Branch for SSPL')
								string(name: 'SSPL_URL', defaultValue: 'https://github.com/Seagate/cortx-monitor.git', description: 'SSPL URL')
						]
					}
		}	
	}		
}	
