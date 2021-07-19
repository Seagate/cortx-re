pipeline {

	agent {
		node {
			label 'Test-node-ssc-vm-c-456'
		}
	}
	parameters {
        string(name: 'PATH1', defaultValue: '/mnt/data1/releases/cortx/github/cortx-1.0/centos-7.8.2003', description: 'Build Path1')
		string(name: 'PATH2', defaultValue: '/mnt/data1/releases/cortx/github/release/rhel-7.7.1908', description: 'Build Path2')
		string(name: 'BUILD', defaultValue: "$BUILD", description: 'BuildNumber')
		choice(name: 'BRANCH', choices: ["main", "custom-ci"], description: 'Branch to be deleted')
		choice(name: 'OS', choices: ["centos-7.8.2003", "centos-7.7.1908"], description: 'OS Version')
        
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
					    THRESHOLD=75
						echo "The Current disk space is $CURRENT "
						if [ "$CURRENT" -gt "$THRESHOLD" ] ; then
						echo Your /mnt/data1/releases partition remaining free space is critically low. Used: $CURRENT%. Threshold: $THRESHOLD%  So, 30 days older files will be deleted $(date)
						#fpath=/mnt/data1/releases
						#source /var/lib/jenkins/workspace/Alert/exclude_build.txt
						
						echo ----Backup of exclude builds--------
						build=$(echo $BUILD | sed -e 's/,/ /g' -e 's/"//g')
						#find ${BUILD1} ${BUILD2} ${BUILD3} -path ${PATH1} -path ${PATH2} -prune -false -o -name '*' -exec cp -R {} /mnt/data1/releases/backups/cortx_build_backup/ \\;
						find $build -path ${PATH1} -path ${PATH2} -prune -false -o -name '*' -exec cp -R {} /mnt/data1/releases/backups/cortx_build_backup/ \\;
						if [ "${BRANCH}" == "main" ] ; then
						echo -----Files to be Deleted from MAIN branch-----
						fpath=/mnt/data1/releases/cortx/github/main/${OS}
						find $fpath -type f -mtime +20 ! -name '*.INFO*' -exec ls -lrt {} + > $WORKSPACE/file1.out
						find $fpath -type f -mtime +20 ! -name '*.INFO*' -exec rm -rf {} \\;
						else
						echo -----Files to be Deleted from CUSTOM-CI branch-----
						fpath=/mnt/data1/releases/cortx/github/custom-ci/${OS}
						find $fpath -type f -mtime +20 ! -name '*.INFO*' -exec ls -lrt {} + > $WORKSPACE/file1.out
						fi
						
						#find $fpath -type f -mtime +20 ! -name '*.INFO*' -exec rm -rf {} \\;
						
						fi
					'''
				}
			}
		}
	post {
		always {
			script {
			        emailext (
					body: "Current Disk Space is ${env.SPACE} : Job ${env.JOB_NAME} : Build URL ${env.BUILD_URL}",
					subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME} : build ${env.BUILD_NUMBER}",
					to: 'CORTX.DevOps.RE@seagate.com',
					)
			}
		}
	}
}

