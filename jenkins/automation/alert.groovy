pipeline {

	agent {
		node {
			label 'Test-node-ssc-vm-c-456'
		}
	}
	environment {
		SPACE = sh(script: "df -h | grep /mnt/data1/releases", , returnStdout: true).trim()
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
					currentBuild.result = 'ABORTED'
					fi
				fi
			'''	
		}
	}
		
		stage ('Threshold alert') {
					steps {
						sh label: 'Threshold alert', script: '''#!/bin/bash
						CURRENT=$(df -h | grep /mnt/data1/releases | awk '{print $5}' | sed 's/%//g')
					        THRESHOLD=70
						echo "The Current disk space is $CURRENT "
						if [ "$CURRENT" -gt "$THRESHOLD" ] ; then
						echo Your /mnt/data1/releases partition remaining free space is critically low. Used: $CURRENT%. Threshold: $THRESHOLD%  So, 30 days older files will be deleted $(date)
						prev_count=0
						fpath=/mnt/data1/releases
						source /mnt/data1/releases/exclude_build.txt
						find $build -path $path1 -path $path2 -prune -false -o -name '*' -exec ls -ltr {} + > /mnt/data1/releases/file1.out
						#touch /mnt/data1/releases/file.out
						#find $fpath -type f -mtime +30  -exec ls -ltr {} + > /mnt/data1/releases/file.out
						#find $fpath -maxdepth 1 ! -type l -print | cut -c1- | grep -v "\\#" -exec ls -ltr {} + > /mnt/data1/releases/file1.out
						##find $fpath -maxdepth 1 ! -type l -print | cut -c1- | grep -v "\\#" &&  find /mnt/data1/releases/cortx/github/release/rhel-7.7.1908/2750 -path /mnt/data1/releases/cortx/github/release/rhel-7.7.1908 -prune -false -o -name '*' && find $fpath ! -name '*.INFO*' && find $fpath -type f -mtime +30  -exec ls -ltr {} + > /mnt/data1/releases/file1.out
						#find $fpath -maxdepth 1 -type l -print | cut -c1- | grep -v "\\#" &&  find /mnt/data1/releases/cortx/github/release/rhel-7.7.1908/2750 -path /mnt/data1/releases/cortx/github/release/rhel-7.7.1908 -prune -false -o -name '*' && find $fpath -name '*.INFO*' && find $fpath -type f -mtime -30  -exec cp {} /mnt/data1/releases/backups/cortx_build_backup/custom_build_backup \\;
						#find $fpath -maxdepth 1 -type l -print | cut -c1- | grep -v "\\#" && find $fpath -name '*.INFO*' && find $fpath -type f -mtime +30  -exec ls -ltr {} + > /mnt/data1/releases/file1.out
						#find $fpath -maxdepth 1 ! -type l -print | cut -c1- | grep -v "\\#" && find $fpath ! -name '*.INFO*' && find $fpath -type f -mtime +30  -exec rm -rf {} \\;
						count=$(cat /mnt/data1/releases/file1.out | wc -l)
							if [ "$prev_count" -lt "$count" ] ; then
							MESSAGE="/mnt/data1/releases/file1.out"
							TO="balaji.ramachandran@seagate.com"
							echo "Files older than 30 days are listed" >> $MESSAGE
							echo "+--------------------------------------------- +" >> $MESSAGE
					    		echo "" >> $MESSAGE
							cat /mnt/data1/releases/file1.out | awk '{print $6,$7,$9}' >> $MESSAGE
							echo "" >> $MESSAGE
							#SUBJECT="WARNING: Your /mnt/data1/releases partition remaining free space is critically low. Used: $CURRENT%.  So, 50 days older files have been deleted $(date)"
							#mailx -s "$SUBJECT" "$TO" < $MESSAGE
							#cat $MESSAGE
							rm -rf $MESSAGE /mnt/data1/releases/file1.out
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
					body: "Current Disk Space is ${env.CURRENT} : Job ${env.JOB_NAME} : Build URL ${env.BUILD_URL}",
					subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME} : build ${env.BUILD_NUMBER}",
					to: 'balaji.ramachandran@seagate.com',
					)
			}
		}
	}
}
