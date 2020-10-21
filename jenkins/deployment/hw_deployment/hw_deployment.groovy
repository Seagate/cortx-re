#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            label "hw_setup_${HOST}"
        }
    }
	
    parameters {
        string(name: 'CORTX_BUILD', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx_builds/211/', description: 'Build URL')
        choice(name: 'HOST', choices: [ 'T1_sm21-r2_sm20-r2', 'T17_smc65-m01_smc66-m01' ], description: 'HW Deploy Host')
        choice(name: 'REIMAGE', choices: ['yes', 'no' ], description: 'Re-Image option')
    }

	environment {

        // CONFIG, NODE_USER, NODE_PASS - Env variables added in the node configurations
        NODE1_HOST=getHostName("${CONFIG}", "1" )
        NODE2_HOST=getHostName("${CONFIG}", "2")
        build_id=sh(script: "echo ${CORTX_BUILD} | rev | cut -d '/' -f2,3 | rev", returnStdout: true).trim()
        NODE_UN_PASS_CRED_ID="736373_manageiq_up"
        CLUSTER_PASS="seagate1"    
    }

    options {
        timeout(time: 180, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        buildDiscarder(logRotator(numToKeepStr: "30"))
    }

    stages {

        stage ('Prerequisite'){
            steps{
                script{
                    
                    manager.addHtmlBadge("&emsp;<b>Build :</b> <a href=\"${CORTX_BUILD}\"><b>${build_id}</b></a> <br /> <b>Host :</b> <a href=\"${CONFIG}\"><b>${HOST}</b></a>")

                    if(!env.CHANGE_PASS){
                        env.CHANGE_PASS = "no"
                    }

                    sh """
                        set +x
                        echo "--------------HW DEPLOYMENT PARAMETERS -------------------"
                        echo "REIMAGE       = ${REIMAGE}"
                        echo "NODE1         = ${NODE1_HOST}"
                        echo "NODE2         = ${NODE2_HOST}"
                        echo "CONFIG_URL    = ${CONFIG}"
                        echo "CORTX_BUILD   = ${CORTX_BUILD}"
                        echo "CHANGE_PASS   = ${CHANGE_PASS}"
                        echo "-----------------------------------------------------------"
                    """

                    dir('cortx-re'){
                        checkout([$class: 'GitSCM', branches: [[name: '*/EOS-14267']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/gowthamchinna/cortx-re/']]])                
                    }
                }
            }
        }

        stage('01. Prepare Environment'){
            when { expression { true } }
            steps{
                script{
                    
                    info("Running '01. Prepare Environment' Stage")  

                    runAnsible("01_PREPARE")
                }
            }
        }
        stage('02. Re-Image System'){
            when { expression { true } }
            steps{
                script{
                    
                    info("Running '02. Re-Image System' Stage")

                     runAnsible("02_REIMAGE")

                }
            }      
        }
        stage('3. Inband Setup'){
            when { expression { true } }
            steps{
                script{
                    
                    info("Running '3. Inband Setup' Stage")

                     runAnsible("03_INBAND")

                }
            } 
            
        }
        stage('4. Disable Cross Connect'){
            when { expression { true } }
            steps{
                script{
                    
                    info("Running '4. Disable Cross Connect' Stage")

                     runAnsible("04_CC_DISABLE")

                }
            } 
        }
        stage('5. Deploy Prereq'){
            when { expression { true } }
            steps{
                script{
                    
                    info("Running '5. Deploy Prereq' Stage")

                    runAnsible("05_DEPLOY_PREREQ")

                }
            } 
        }
        stage('6. Deploy Cortx Stack'){
            when { expression { true } }
            steps{
                script{
                    
                    info("Running '6. Deploy Cortx Stack' Stage")

                    runAnsible("06_DEPLOY")

                }
            } 
        }
        stage('7. Enable Cross Connect'){
            when { expression { true } }
            steps{
                script{
                    
                    info("Running '7. Enable Cross Connect' Stage")

                    runAnsible("07_CC_ENABLE")

                }
            } 
        }
        stage('8. Check PCS Status'){
            when { expression { true } }
            steps{
                script{
                    
                    info("Running '8. Check PCS Status' Stage")

                }
            } 
        }
        stage('9. Check Service Status'){
            when { expression { true } }
            steps{
                script{

                    info("Running '9. Check Service Status' Stage")

                }
            } 
        }
        stage('10. Run Sanity'){
            when { expression { true } }
            steps{
                script{
                    info("Running '10. Run Sanity' Stage")

                }
            } 
        }
	}

    post { 
        always {
            script{
                cleanWs()
            }
        }
    }
}	


// Method returns host Name from confi
def getHostName(configPath, hostSearch){

    return sh(script: """
                set +x
                wget -q "${configPath}" -O config.ini
                echo \$(grep -A 5  "\\[srvnode\\-${hostSearch}\\]" config.ini | grep hostname= | cut -d = -f2)
            """, returnStdout: true).trim()
}

// Method returns VM Host Information ( host, ssh cred)
def getTestMachine(host, user, pass){

    def remote = [:]
    remote.name = 'cortx'
    remote.host = host
    remote.user =  user
    remote.password = pass
    remote.allowAnyHosts = true
    remote.fileTransfer = 'scp'
    return remote
}


def runAnsible(tags){
    withCredentials([usernamePassword(credentialsId: "${NODE_UN_PASS_CRED_ID}", passwordVariable: 'SERVICE_PASS', usernameVariable: 'SERVICE_USER'),usernamePassword(credentialsId: "RE-SAT-CRED", passwordVariable: 'SATELLITE_PW', usernameVariable: 'SATELLITE_UN')]) {
        
        dir("cortx-re/scripts/deployment"){
            ansiblePlaybook(
                playbook: 'cortx_deploy_hw.yml',
                inventory: 'inventories/hw_deployment/hosts',
                tags: "${tags}",
                extraVars: [
                    "REIMAGE"       : [value: "${REIMAGE}", hidden: false],
                    "NODE1"         : [value: "${NODE1_HOST}", hidden: false],
                    "NODE2"         : [value: "${NODE2_HOST}", hidden: false] ,
                    "CONFIG_URL"    : [value: "${CONFIG}", hidden: false],
                    "BUILD_URL"     : [value: "${CORTX_BUILD}", hidden: false],
                    "CLUSTER_PASS"  : [value: "${CLUSTER_PASS}", hidden: true],
                    "SATELLITE_UN"  : [value: "${SATELLITE_UN}", hidden: true],
                    "SATELLITE_PW"  : [value: "${SATELLITE_PW}", hidden: true],
                    "SERVICE_USER"  : [value: "${SERVICE_USER}", hidden: true],
                    "SERVICE_PASS"  : [value: "${SERVICE_PASS}", hidden: true],
                    "CHANGE_PASS"   : [value: "${CHANGE_PASS}", hidden: false]
                ],
                //extras: '-vvv',
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