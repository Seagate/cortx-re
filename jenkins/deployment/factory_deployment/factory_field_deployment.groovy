#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            label "hw_setup_${HOST}"
        }
    }
	
    parameters {
        string(name: 'CORTX_BUILD', defaultValue: '', description: 'Build URL', trim: true)
	string(name: 'MGMT_VIP', defaultValue: '', description: 'Management VIP', trim: true)
        string(name: 'HOST', defaultValue: '' , description: 'HW Deploy Host', trim: true)
        string(name: 'CONFIG', defaultValue: '' , description: 'HW config file raw file path', trim: true)
        choice(name: 'DEPLOY_TYPE', choices: [ 'DEPLOY-WITH-REIMAGE','DEPLOY-WITHOUT-REIMAGE','REIMAGE' ], description: 'Deployment Type')
    }

	environment {

 
        build_id = getBuild("${CORTX_BUILD}")
        NODES = getHosts("${CONFIG}")
        USERS = getUsers("${CONFIG}")

        ISO_PARENT_DIR = sh(script: "set +x; echo \$(dirname ${CORTX_BUILD})", returnStdout: true).trim()
        CORTX_PREP_NAME = getBuildArtifcatName("${ISO_PARENT_DIR}/", 'cortx-prep')
        CORTX_OS_ISO_NAME = getBuildArtifcatName("${ISO_PARENT_DIR}/", 'cortx-os')
        CORTX_PREP_URL = "${ISO_PARENT_DIR}/${CORTX_PREP_NAME}"
        CORTX_OS_ISO_URL = "${ISO_PARENT_DIR}/${CORTX_OS_ISO_NAME}"
	
	NODE_UN_PASS_CRED_ID = "cortx-re-cloudform"
        RE_SAT_CRED_ID       = "RE-SAT-CRED"
        NODE_DEFAULT_SSH_CRED  = credentials("hw-deployment-ssh-cred")
        NODE_USER = "${NODE_DEFAULT_SSH_CRED_USR}"
        NODE_PASS = "${NODE_DEFAULT_SSH_CRED_PSW}"
        CLUSTER_PASS = "${NODE_DEFAULT_SSH_CRED_PSW}"
    }

    options {
        timeout(time: 300, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        buildDiscarder(logRotator(numToKeepStr: "30"))
    }

    stages {

        stage ('Prerequisite') {
            steps {
                script {
                    
                    manager.addHtmlBadge("&emsp;<b>Build :</b> <a href=\"${CORTX_BUILD}\"><b>${build_id}</b></a> <br /> <b>Host :</b> <a href=\"${CONFIG}\"><b>${HOST}</b></a>")

                    sh """
                        set +x
                        echo "--------------HW DEPLOYMENT PARAMETERS -------------------"
                        
                        echo "NODES                 = ${NODES}"
                        echo "CONFIG_URL            = ${CONFIG}"
                        echo "CORTX_BUILD           = ${CORTX_BUILD}"
                        echo "CORTX_PREP_URL        = ${CORTX_PREP_URL}"
                        echo "CORTX_OS_ISO_URL      = ${CORTX_OS_ISO_URL}"
                        echo "-----------------------------------------------------------"
                    """
                    dir('cortx-re') {
                        checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/seagate/cortx-re']]])
                    }
                }
            }
        }
        

        
        stage('00. Prepare Environment') {
            steps {
                script {


                    
                    info("Running '00. Prepare Environment' Stage")  

                    runAnsible("00_PREPARE")
                }
            }
        }
        
        stage('Re-Image System') {
            when { expression { DEPLOY_TYPE != "DEPLOY-WITHOUT-REIMAGE" } }
            steps {
                script {
                    
                    info("Running 'Re-Image System' Stage")

                     runAnsible("REIMAGE")

                }
            }      
        }
        
        stage('01. Preparation Startup') {
            when { expression { DEPLOY_TYPE == "DEPLOY-WITH-REIMAGE" || DEPLOY_TYPE == "DEPLOY-WITHOUT-REIMAGE"  } }
            steps {
                script {
                    
                    info("Running '01. Preparation startup' Stage")  

                    runAnsible("01_PREPARE_STARTUP")
                }
            }
        }
    
        stage('02. Factory Manufacturing') {
            when { expression { DEPLOY_TYPE == "DEPLOY-WITH-REIMAGE" || DEPLOY_TYPE == "DEPLOY-WITHOUT-REIMAGE"  } }
            steps {
                script {
                    
                    info("Running '02. Factory Manufacturing' Stage")

                     runAnsible("02_FACTORY_MANUFACTURING")

                }
            }      
        }

        stage('03. Factory Manufacturing Configure Security') {
            when { expression { DEPLOY_TYPE == "DEPLOY-WITH-REIMAGE" || DEPLOY_TYPE == "DEPLOY-WITHOUT-REIMAGE"  } }
            steps {
                script {
                    
                    info("Running '03. Factory Manufacturing configure security' Stage")

                     runAnsible("03_SECURITY")

                }
            }      
        }
		
        stage('04. Node Configuration') {
            when { expression { DEPLOY_TYPE == "DEPLOY-WITH-REIMAGE" || DEPLOY_TYPE == "DEPLOY-WITHOUT-REIMAGE"  } }
            steps {
                script {
                    
                    info("Running '04. Node Configuration' Stage")

                    runAnsible("04_NODE_CONFIGURATION")

                }
            } 
        }
		
        stage('05. Prepare node') {
            when { expression { DEPLOY_TYPE == "DEPLOY-WITH-REIMAGE" || DEPLOY_TYPE == "DEPLOY-WITHOUT-REIMAGE"  } }
            steps {
                script {
                    
                    info("Running '05. Prepare node Configuration' Stage")

                    runAnsible("05_PREPARE_NODE")

                }
            } 
        }
		
        stage('06. Network config') {
            when { expression { DEPLOY_TYPE == "DEPLOY-WITH-REIMAGE" || DEPLOY_TYPE == "DEPLOY-WITHOUT-REIMAGE"  } }
            steps {
                script {
                    
                    info("Running '06. Network Configuration' Stage")

                    runAnsible("06_NETWORK_CONFIG")

                }
            } 
        }
		
        stage('07. System config') {
            when { expression { DEPLOY_TYPE == "DEPLOY-WITH-REIMAGE" || DEPLOY_TYPE == "DEPLOY-WITHOUT-REIMAGE"  } }
            steps {
                script {
                    
                    info("Running '07. System Configuration' Stage")

                    runAnsible("07_SYSTEM_CONFIG")

                }
            } 
        }

        stage('08. Cluster config') {
            when { expression { DEPLOY_TYPE == "DEPLOY-WITH-REIMAGE" || DEPLOY_TYPE == "DEPLOY-WITHOUT-REIMAGE"  } }
            steps {
                script {
                    
                    info("Running '08. Cluster Configuration' Stage")

                    runAnsible("08_CLUSTER_CONFIG")

                }
            } 
        }
		
        stage('09. Storage config') {
            when { expression { DEPLOY_TYPE == "DEPLOY-WITH-REIMAGE" || DEPLOY_TYPE == "DEPLOY-WITHOUT-REIMAGE"  } }
            steps {
                script {
                    
                    info("Running '09. Storage Configuration' Stage")

                    runAnsible("09_STORAGE_NETWORK")

                }
            } 
        }
		
        stage('10. Prepare config Cluster') {
            when { expression { DEPLOY_TYPE == "DEPLOY-WITH-REIMAGE" || DEPLOY_TYPE == "DEPLOY-WITHOUT-REIMAGE"  } }
            steps {
                script {
                    
                    info("Running '10. Prepare Configure Cluster' Stage")

                    runAnsible("10_PREPARE_CONFIGURE")

                }
            } 
        }
		
        stage('11. Start Cluster') {
            when { expression { DEPLOY_TYPE == "DEPLOY-WITH-REIMAGE" || DEPLOY_TYPE == "DEPLOY-WITHOUT-REIMAGE"  } }
            steps {
                script {
                    
                    info("Running '11. Start Cluster' Stage")

                    runAnsible("11_START_CLUSTER")

                }
            } 
        }  
			
	}
	
	post { 
        always {
            script {
                // POST ACTIONS

                // 1. Download Log files from Deployment Machine
                try {
                    sh label: 'download_log_files', returnStdout: true, script: ''' 
                        mkdir -p artifacts/srvnode1 
                        mkdir -p artifacts/srvnode2 
                        mkdir -p artifacts/srvnode3
                        counter=1
                        IFS=',' read -ra NODELIST <<< "${NODES}"
                        for NODE in "${NODELIST[@]}"; do
                            sshpass -p "${NODE_PASS}" scp -r -o StrictHostKeyChecking=no "${NODE_USER}"@"${NODE}":/root/cortx_deployment artifacts/srvnode${counter} &>/dev/null || true
                            counter=$((counter+1))
                        done
                    '''
                } catch (err) {
                    echo err.getMessage()
                }

                // 2. Archive Deployment artifacts in jenkins build
                archiveArtifacts artifacts: "artifacts/**/*.*", onlyIfSuccessful: false, allowEmptyArchive: true
                cleanWs()
            }
        }
	}    	    
}	

// Method returns host Names from config

def getHosts(configPath) {

    return sh(script: """
                set +x
                prefix=""
                nodenames=""
                wget -q "${configPath}" -O config.ini
                node_count="\$(cat config.ini | grep 'srvnode-' | wc -l)"
                for node in \$(seq 1 \$node_count); do
                    host="\$(grep -A 5  "\\[srvnode\\-\$node\\]" config.ini | grep ansible_host= | cut -d = -f2)"		
                    nodenames+="\$prefix\$host"
                    prefix=","
                    node+=1
                done
                echo "\$nodenames"
            """, returnStdout: true).trim()
}

def getUsers(configPath) {

    return sh(script: """
                set +x
                prefix=""
                usernames=""
                wget -q "${configPath}" -O config.ini
                user_count="\$(cat config.ini | grep 'srvnode-' | wc -l)"
                for users in \$(seq 1 \$user_count); do
                    host_user="\$(grep -A 5  "\\[srvnode\\-\$users\\]" config.ini | grep ansible_user= | cut -d = -f2)"		
                    usernames+="\$prefix\$host_user"
                    prefix=","
                    users+=1
                done
                echo "\$usernames"
            """, returnStdout: true).trim()
}

// Get build id from build url
def getBuild(buildURL) {

    buildID = sh(script: "curl -s  $buildURL/RELEASE.INFO  | grep BUILD | cut -d':' -f2 | tr -d '\"' | xargs", returnStdout: true).trim()
    buildBranch = sh(script: "curl -s  $buildURL/RELEASE.INFO  | grep BRANCH | cut -d':' -f2 | tr -d '\"' | xargs", returnStdout: true).trim()

 return "$buildBranch#$buildID"   
}

// Method returns host Name from config
def getHostName(configPath, hostSearch) {

    return sh(script: """
                set +x
                wget -q "${configPath}" -O config.ini
                echo \$(grep -A 5  "\\[srvnode\\-${hostSearch}\\]" config.ini | grep hostname= | cut -d = -f2)
            """, returnStdout: true).trim()
}

// Method returns host Name from config
def getBuildArtifcatName(path, artifcat) {

    return sh(script: '''set +x ; echo $(curl -s ''' +path +''' | sed -n 's/.*href="\\([^"]*\\).*/\\1/p' | grep ''' +artifcat +''')''', returnStdout: true).trim()
}

// Method returns VM Host Information ( host, ssh cred)
def getTestMachine(host, user, pass) {

    def remote = [:]
    remote.name = 'cortx'
    remote.host = host
    remote.user =  user
    remote.password = pass
    remote.allowAnyHosts = true
    remote.fileTransfer = 'scp'
    return remote
}


def runAnsible(tags) {
    withCredentials([usernamePassword(credentialsId: "${NODE_UN_PASS_CRED_ID}", passwordVariable: 'SERVICE_PASS', usernameVariable: 'SERVICE_USER'), usernamePassword(credentialsId: "${RE_SAT_CRED_ID}", passwordVariable: 'SATELLITE_PW', usernameVariable: 'SATELLITE_UN')]) { 
        
        dir("cortx-re/scripts/deployment") {
            ansiblePlaybook(
                playbook: 'cortx_deploy_factory.yml',
                inventory: 'inventories/factory_deployment/hosts_srvnodes',
                tags: "${tags}",
                extraVars: [
                    
                    "DEPLOY_TYPE"           : [value: "${DEPLOY_TYPE}", hidden: false],
                    "CONFIG_URL"            : [value: "${CONFIG}", hidden: false],
                    "CORTX_BUILD_URL"       : [value: "${CORTX_BUILD}", hidden: false],
                    "CORTX_PREP_URL"        : [value: "${CORTX_PREP_URL}", hidden: false],
                    "CORTX_OS_ISO_URL"      : [value: "${CORTX_OS_ISO_URL}", hidden: false],
                    "CLUSTER_PASS"          : [value: "${CLUSTER_PASS}", hidden: true],
                    "SATELLITE_UN"          : [value: "${SATELLITE_UN}", hidden: true],
                    "SATELLITE_PW"          : [value: "${SATELLITE_PW}", hidden: true],
					"MGMT_VIP"				: [value: "${MGMT_VIP}", hidden: true],
					"USERS"                 : [value: "${USERS}", hidden: true]
                ],
                extras: '-v',
                colorized: true
            )
        }
    }
}

def info(msg) {
    echo "--------------------------------------------------------------"
    echo "\033[44m[Info] : ${msg} \033[0m"
    echo "--------------------------------------------------------------"
}
def error(msg) {
    echo "--------------------------------------------------------------"
    echo "\033[1;31m[Error] : ${msg} \033[0m"
    echo "--------------------------------------------------------------"
}
def success(msg) {
    echo "--------------------------------------------------------------"
    echo "\033[1;32m[Success] : ${msg} \033[0m"
    echo "--------------------------------------------------------------"
}
