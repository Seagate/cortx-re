pipeline {

	agent {
		node {
			label 'Test-node-ssc-vm-c-456'
		}
	}
<<<<<<< HEAD
=======
	environment {
		SPACE=sh(script: "df -h | grep /mnt/data1/releases", , returnStdout: true).trim()
	}
>>>>>>> f344d8eb4b9f9085655c1a12ab29049278df5078
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
<<<<<<< HEAD
=======
					currentBuild.result = 'ABORTED'
>>>>>>> f344d8eb4b9f9085655c1a12ab29049278df5078
					fi
				fi
			'''	
		}
	}
		
		stage ('Threshold alert') {
					steps {
						sh label: 'Threshold alert', script: '''#!/bin/bash
						CURRENT=$(df -h | grep /mnt/data1/releases | awk '{print $5}' | sed 's/%//g')
<<<<<<< HEAD
						THRESHOLD=94
=======
						THRESHOLD=95
						echo "The Current disk space is $CURRENT "
>>>>>>> f344d8eb4b9f9085655c1a12ab29049278df5078
						if [ "$CURRENT" -gt "$THRESHOLD" ] ; then
						echo Your /mnt/data1/releases partition remaining free space is critically low. Used: $CURRENT%. Threshold: $THRESHOLD%  So, 30 days older files will be deleted $(date)
						prev_count=0
						fpath=/mnt/data1/releases
						find $fpath -type f -mtime +30  -exec ls -ltr {} + > /mnt/data1/releases/file.out
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
	post {
		always {
			script {
			        emailext (
<<<<<<< HEAD
					body: '''${SCRIPT, template="release-email.template"}''',
					mimeType: 'text/html',
=======
					body: "Current Disk Space is ${env.SPACE}",
>>>>>>> f344d8eb4b9f9085655c1a12ab29049278df5078
					subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
					attachLog: true,
					to: ('priyank.p.dalal@seagate.com,balaji.ramachandran@seagate.com,shailesh.vaidya@seagate.com,mukul.malhotra@seagate.com'),
					)
			}
		}
	}
}
<<<<<<< HEAD

=======
>>>>>>> f344d8eb4b9f9085655c1a12ab29049278df5078
