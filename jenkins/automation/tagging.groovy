pipeline {

        agent {
                node {
                        label 'docker-cp-centos-7.8.2003-node'
                }
        }

        parameters {
                string(name: 'RELEASE_INFO_URL', defaultValue: '', description: 'RELEASE BUILD')
                //string(name: 'THIRD_PARTY_RELEASE_INFO_URL', defaultValue: '', description: 'THIRD PARTY RELEASE BUILD')
				string(name: 'GIT_TAG', defaultValue: '', description: 'Release Tag')
				string(name: 'USER_NAME', defaultValue: '', description: 'User Name')
				string(name: 'PASSWD', defaultValue: '', description: 'Token')
				string(name: 'USER_EMAIL', defaultValue: '', description: 'User Email')
        }
		
		
    environment {
                COMMIT_HASH_CORTX_CSM_AGENT = get_commit_hash("cortx-csm_agent", "${RELEASE_INFO_URL}")
                COMMIT_HASH_CORTX_CSM_WEB = get_commit_hash("cortx-csm_web", "${RELEASE_INFO_URL}")
                COMMIT_HASH_CORTX_HARE = get_commit_hash("cortx-hare", "${RELEASE_INFO_URL}")
                COMMIT_HASH_CORTX_HA = get_commit_hash("cortx-ha", "${RELEASE_INFO_URL}")
                COMMIT_HASH_CORTX_MOTR = get_commit_hash("cortx-motr", "${RELEASE_INFO_URL}")
                COMMIT_HASH_CORTX_PRVSNR = get_commit_hash("cortx-prvsnr", "${RELEASE_INFO_URL}")
                COMMIT_HASH_CORTX_S3SERVER = get_commit_hash("cortx-s3server", "${RELEASE_INFO_URL}")
                COMMIT_HASH_CORTX_SSPL = get_commit_hash("cortx-sspl", "${RELEASE_INFO_URL}")
            }

        stages {

        stage ("Display") {
            steps {
                script { build_stage=env.STAGE_NAME }
                echo "COMMIT_HASH_CORTX_CSM_AGENT = $COMMIT_HASH_CORTX_CSM_AGENT"
                echo "COMMIT_HASH_CORTX_CSM_WEB = $COMMIT_HASH_CORTX_CSM_WEB"
                echo "COMMIT_HASH_CORTX_HARE = $COMMIT_HASH_CORTX_HARE"
                echo "COMMIT_HASH_CORTX_HA = $COMMIT_HASH_CORTX_HA"
                echo "COMMIT_HASH_CORTX_MOTR = $COMMIT_HASH_CORTX_MOTR"
                echo "COMMIT_HASH_CORTX_PRVSNR = $COMMIT_HASH_CORTX_PRVSNR"
                echo "COMMIT_HASH_CORTX_S3SERVER = $COMMIT_HASH_CORTX_S3SERVER"
                echo "COMMIT_HASH_CORTX_SSPL = $COMMIT_HASH_CORTX_SSPL"
            }
        }
		
		stage ("Tagging") {
			steps {
				script { build_stage=env.STAGE_NAME }
				script { 
				sh label: 'Git Tagging', script: '''#!/bin/bash
				declare -A COMPONENT_LIST=( 
						[cortx-s3server]="https://$PASSWD@github.com/$USER_NAME/cortx-s3server.git"
						[cortx-motr]="https://$PASSWD@github.com/$USER_NAME/cortx-motr.git"
						[cortx-hare]="https://$PASSWD@github.com/$USER_NAME/cortx-hare.git"
						[cortx-ha]="https://$PASSWD@github.com/$USER_NAME/cortx-ha.git"
						[cortx-prvsnr]="https://$PASSWD@github.com/$USER_NAME/cortx-prvsnr.git"
						[cortx-sspl]="https://$PASSWD@github.com/$USER_NAME/cortx-monitor.git"
						[cortx-csm_agent]="https://$PASSWD@github.com/$USER_NAME/cortx-manager.git"
						[cortx-csm_web]="https://$PASSWD@github.com/$USER_NAME/cortx-management-portal.git"
					)

					wget -q $RELEASE_INFO_URL -O RELEASE.INFO
					for component in "${!COMPONENT_LIST[@]}"
					do
						dir=$(echo ${COMPONENT_LIST[$component]} |  awk -F'/' '{print $NF}')
						git clone --quiet ${COMPONENT_LIST[$component]} $dir > /dev/null

						rc=$?
						if [ $rc -ne 0 ]; then 
							echo "ERROR:git clone failed for $component"
							exit 1
						fi

						if [ $component == cortx-hare ] || [ $component == cortx-sspl ] || [ $component == cortx-ha ] || [ $component == cortx-fs ]; then
							COMMIT_HASH=$(grep $component RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | cut -d. -f1 |  sed 's/git//g');
						elif [ "$component" == "cortx-csm_agent" ] || [ "$component" == "cortx-csm_web" ]; then
							COMMIT_HASH=$(grep $component RELEASE.INFO | head -1 | awk -F['_'] '{print $3}' |  cut -d. -f1);
						else
							COMMIT_HASH=$(grep $component RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | sed 's/git//g');
						fi

						echo "Component: $component , Repo:  ${COMPONENT_LIST[$component]}, Commit Hash: ${COMMIT_HASH}"
						pushd $dir
							git config --global user.email "USER_EMAIL"
							git config --global user.name "$USER_NAME"
							git tag -a "$GIT_TAG" "${COMMIT_HASH}"
							git push origin "$GIT_TAG"
							echo "Component: $component , Tag: git tag -l $GIT_TAG is Tagged Successfully"
						popd
						
					done
				'''
				}				
			}
		}
	}
}    
     
 
def get_commit_hash(String component, String release_info) {

    return sh(script: """
            set +x
            if [ $component == cortx-hare ] || [ $component == cortx-sspl ] || [ $component == cortx-ha ] || [ $component == cortx-fs ]; then
                    echo \$(curl -s $release_info | grep $component | head -1 | awk -F['_'] '{print \$2}' | cut -d. -f1 |  sed 's/git//g');
					
            elif [ "$component" == "cortx-csm_agent" ] || [ "$component" == "cortx-csm_web" ]; then
                    echo \$(curl -s $release_info | grep $component | head -1 | awk -F['_'] '{print \$3}' |  cut -d. -f1);
					
            else
                    echo \$(curl -s $release_info | grep $component | head -1 | awk -F['_'] '{print \$2}' | sed 's/git//g');
					
            fi
			
			""", returnStdout: true).trim()
}



