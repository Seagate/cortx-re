pipeline {

	agent {
		node {
			label 'Test-node-ssc-vm-c-456'
		}
	}
		
	parameters {
		string(name: 'RELEASE_INFO_URL', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx_builds/centos-7.8.2003/552/RELEASE.INFO', description: 'RELEASE BUILD')
		string(name: 'THIRD_PARTY_RELEASE_INFO_URL', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx_builds/centos-7.8.2003/552/THIRD_PARTY_RELEASE.INFO', description: 'THIRD PARTY RELEASE BUILD')
	}
    
    environment {
		COMMIT_HASH_CORTX_CSM_AGENT=get_commit_hash("cortx-csm_agent", "${RELEASE_INFO_URL}")
		COMMIT_HASH_CORTX_CSM_WEB=get_commit_hash("cortx-csm_web", "${RELEASE_INFO_URL}")
		COMMIT_HASH_CORTX_HARE=get_commit_hash("cortx-hare", "${RELEASE_INFO_URL}")
		COMMIT_HASH_CORTX_HA=get_commit_hash("cortx-ha", "${RELEASE_INFO_URL}")
		COMMIT_HASH_CORTX_MOTR=get_commit_hash("cortx-motr", "${RELEASE_INFO_URL}")
		COMMIT_HASH_CORTX_PRVSNR=get_commit_hash("cortx-prvsnr", "${RELEASE_INFO_URL}")
		COMMIT_HASH_CORTX_S3SERVER=get_commit_hash("cortx-s3server", "${RELEASE_INFO_URL}")
		COMMIT_HASH_CORTX_SSPL=get_commit_hash("cortx-sspl", "${RELEASE_INFO_URL}")
	    	THIRD_PARTY_RELEASE_VERSION=get_version("${THIRD_PARTY_VERSION}")
	    	THIRD_PARTY_RELEASE_INFO=""
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
		
		
	stage ("Build custom-ci") {
             when { expression { true } }
            steps {
                script { build_stage=env.STAGE_NAME }
                build job: 'custom-ci', wait: true,
                parameters: [
					string(name: 'THIRD_PARTY_RELEASE_VERSION', value: "${THIRD_PARTY_VERSION}"),
                    string(name: 'CSM_AGENT_BRANCH', value: "${COMMIT_HASH_CORTX_CSM_AGENT}"),
                    string(name: 'CSM_WEB_BRANCH', value: "${COMMIT_HASH_CORTX_CSM_WEB}"),
                    string(name: 'HARE_BRANCH', value: "${COMMIT_HASH_CORTX_HARE}"),
                    string(name: 'HA_BRANCH', value: "${COMMIT_HASH_CORTX_HA}"),
                    string(name: 'MOTR_BRANCH', value: "${COMMIT_HASH_CORTX_MOTR}"),
                    string(name: 'PRVSNR_BRANCH', value: "${COMMIT_HASH_CORTX_PRVSNR}"),
                    string(name: 'S3_BRANCH', value: "${COMMIT_HASH_CORTX_S3SERVER}"),
                    string(name: 'SSPL_BRANCH', value: "${COMMIT_HASH_CORTX_SSPL}")
                        
                ]
            }
		}	
	}		
}


def get_commit_hash(String component, String release_info){

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

def get_version(String THIRD_PARTY_VERSION){
	return sh(script: """
			THIRD_PARTY_RELEASE_INFO=wget ${THIRD_PARTY_RELEASE_INFO_URL}
			echo "Third Party File is $THIRD_PARTY_RELEASE_INFO"
			THIRD_PARTY_VERSION=cat $THIRD_PARTY_RELEASE_INFO | grep THIRD_PARTY_VERSION | awk print "${2}" | cut -b 18-24
			echo "THIRD_PARTY_VERSION = $THIRD_PARTY_VERSION"
			""", returnStdout:trim).trim()
}			
