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
		string(name: 'CSM_AGENT_BRANCH', defaultValue: 'main', description: 'Branch for CSM Agent')
		string(name: 'CSM_AGENT_URL', defaultValue: 'https://github.com/Seagate/cortx-manager', description: 'CSM_AGENT URL')
		string(name: 'CSM_WEB_BRANCH', defaultValue: 'main', description: 'Branch for CSM Web')
		string(name: 'CSM_WEB_URL', defaultValue: 'https://github.com/Seagate/cortx-management-portal', description: 'CSM WEB URL')
		string(name: 'HARE_BRANCH', defaultValue: 'main', description: 'Branch for Hare')
		string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Hare URL')
		string(name: 'HA_BRANCH', defaultValue: 'main', description: 'Branch for Cortx-HA')
		string(name: 'HA_URL', defaultValue: 'https://github.com/Seagate/cortx-ha.git', description: 'Cortx-HA URL')
		string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch for Motr')
		string(name: 'MOTR_URL', defaultValue: 'https://github.com/Seagate/cortx-motr.git', description: 'Motr URL')
		string(name: 'PRVSNR_BRANCH', defaultValue: 'main', description: 'Branch for Provisioner')
		string(name: 'PRVSNR_URL', defaultValue: 'https://github.com/Seagate/cortx-prvsnr.git', description: 'Provisioner URL')
		string(name: 'S3_BRANCH', defaultValue: 'main', description: 'Branch for S3Server')
		string(name: 'S3_URL', defaultValue: 'https://github.com/Seagate/cortx-s3server.git', description: 'S3Server URL')
		string(name: 'SSPL_BRANCH', defaultValue: 'main', description: 'Branch for SSPL')
		string(name: 'SSPL_URL', defaultValue: 'https://github.com/Seagate/cortx-monitor.git', description: 'SSPL URL')
    		
		
		choice(
			name: 'OTHER_COMPONENT_BRANCH',
			choices: ['main', 'stable', 'Cortx-v1.0.0_Beta', 'cortx-1.0'],
			description: 'Branch name to pick-up other components rpms'
		)
	}
	stages {
		stage ('CheckMount') {
		    steps {
				sh label: 'CheckMount', script: '''#!/bin/bash
		        	mount="cortx-storage.colo.seagate.com:/mnt/data1/releases"
				if grep -qs "$mount" /proc/mounts;then
				echo "cortx-storage.colo.seagate.com:/mnt/data1/releases is mounted."
				else
				echo "cortx-storage.colo.seagate.com:/mnt/data1/releases is not mounted."
				mount -t nfs4 "$mount" /mnt/data1/releases
					if [ $? -eq 0 ]; then
					echo "Mount success!"
					else
					echo "Something went wrong with the mount..."
					fi
				fi
			'''	
		}
	}
		
		stage ('Threshold alert') {
					steps {
						sh label: 'Threshold alert', script: '''#!/bin/bash
						CURRENT=$(df -h | grep /mnt/data1/releases | awk '{print $5}' | sed 's/%//g')
						THRESHOLD=95
						if [ "$CURRENT" -gt "$THRESHOLD" ] ; then
						echo Your /mnt/data1/releases partition remaining free space is critically low. Used: $CURRENT%. Threshold: $THRESHOLD%  So, 30 days older files will be deleted $(date)
						prev_count=0
						fpath=/mnt/data1/releases
						#find $fpath -type f -mtime +30  -exec ls -ltr {} + > /mnt/data1/releases/file.out
						find $fpath -maxdepth 1 -type l -print | cut -c3- | grep -v "\#" && find . -name '*.sh*' -type f -mtime +30  -exec ls -ltr {} + > /mnt/data1/releases/file.out
						count=$(cat /mnt/data1/releases/file.out | wc -l)
							if [ "$prev_count" -lt "$count" ] ; then
							MESSAGE="/mnt/data1/releases/file1.out"
							TO="balaji.ramachandran@seagate.com"
							echo "Files older than 30 days are listed" >> $MESSAGE
							echo "+--------------------------------------------- +" >> $MESSAGE
					    		echo "" >> $MESSAGE
							cat /mnt/data1/releases/file.out | awk '{print $6,$7,$9}' >> $MESSAGE
							echo "" >> $MESSAGE
							#SUBJECT="WARNING: Your /mnt/data1/releases partition remaining free space is critically low. Used: $CURRENT%.  So, 50 days older files have been deleted $(date)"
							#mailx -s "$SUBJECT" "$TO" < $MESSAGE
							cat $MESSAGE
							rm $MESSAGE /mnt/data1/releases/file.out
							fi
						fi
					'''
				}
			}
		}
		stage ('commit hash') {
					steps {
						sh label: 'commit hash', script: '''#!/bin/bash
						set +x
						while [[ "$#" -gt 0 ]]; do
						case $1 in
						-r|--release) RELEASE_INFO="$2"; shift ;;
						*) echo "Unknown parameter passed: $1"; exit 1 ;;
						esac
						shift
					done

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
	post {
		always {
			script {
			        emailext (
					body: '''${SCRIPT, template="release-email.template"}''',
					mimeType: 'text/html',
					subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
					attachLog: true,
					to: 'balaji.ramachandran@seagate.com',
					)
			}
		}
	}
}
