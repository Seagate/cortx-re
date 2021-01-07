pipeline {

	agent {
		node {
			label 'Test-node-ssc-vm-c-456'
		}
	}
	 triggers {
         cron('0 */6 * * *')
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
						find $fpath -maxdepth 1 -type l -print | cut -c3- | grep -v "\\#" && find . -name '*.sh*' -type f -mtime +30  -exec ls -ltr {} + > /mnt/data1/releases/file.out
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
						
		stage ("Build buildretention") {
					steps {
						script { build_stage=env.STAGE_NAME }
						build job: 'buildretention', wait: true,
						parameters: [
								string(name: 'RELEASE_INFO_URL', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx_builds/centos-7.8.2003/552/RELEASE.INFO', description: 'RELEASE BUILD')
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
