#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            label "hw_setup_${HOST}"
        }
    }
	
    parameters {
        string(name: 'CORTX_BUILD', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx_builds/211/', description: 'Build URL')
        choice(name: 'HOST', choices: [ 'T1_sm21-r2_sm20-r2', 'T2_sm28-r5_sm27-r5', 'T3_sm27-r22_sm28-r22', 'T4_sm14-r24_sm13-r24', 'T5_sm13-r4_sm6-r4', 'T6_sm35-r5_sm34-r5', 'T7_sm10-r20_sm11-r20', 'T8_sm28-r20_sm29-r20', 'T9_smc5-m11_smc6-m11', 'T10_sm8-r19_sm7-r19', 'T11_sm26-r19_sm27-r19', 'T12_sm10-r21_sm11-r21', 'T13_sm17-r18_sm18-r18', 'T14_sm27-r23_sm28-r23', 'T15_smc33-m09_smc34-m09', 'T16_sm20-r22_sm21-r22', 'T17_smc65-m01_smc66-m01', 'T18_smc7-m11_smc8-m11', 'T19_smc39-m09_smc40-m09' ], description: 'HW Deploy Host')
        choice(name: 'REIMAGE', choices: [ "yes", "no" ], description: 'Re-Image option')
    }

	environment {

        // CONFIG, NODE_USER, NODE_PASS - Env variables added in the node configurations
        NODE1_HOST=getHostName("${CONFIG}", "1" )
        NODE2_HOST=getHostName("${CONFIG}", "2")
        build_id=sh(script: "echo ${CORTX_BUILD} | rev | cut -d '/' -f2,3 | rev", returnStdout: true).trim()
        NODE_UN_PASS_CRED_ID="736373_manageiq_up"
        CLUSTER_PASS="seagate1"  

        STAGE_01_PREPARE="yes"
        STAGE_02_REIMAGE="${REIMAGE}"
        STAGE_03_DEPLOY_PREREQ="yes"
        STAGE_04_INBAND="yes"
        STAGE_05_CC_DISABLE="yes"
        STAGE_06_DEPLOY="yes"
        STAGE_07_CC_ENABLE="yes"

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
            when { expression { env.STAGE_01_PREPARE == "yes" } }
            steps{
                script{
                    
                    info("Running '01. Prepare Environment' Stage  [ validates input param, passwordless ssh, installs required tools ]")  

                    runAnsible("01_PREPARE")
                }
            }
        }
        stage('02. Re-Image System'){
            when { expression { env.STAGE_02_REIMAGE == "yes"  } }
            steps{s
                script{
                    
                    info("Running '02. Re-Image System' Stage")

                     runAnsible("02_REIMAGE")

                }
            }      
        }
        stage('03. Deploy Prereq'){
            when { expression { env.STAGE_03_DEPLOY_PREREQ == "yes" } }
            steps{
                script{
                    
                    info("Running '03. Deploy Prereq' Stage [ multipath fix, cortx-prereq install, config download ]")

                    runAnsible("03_DEPLOY_PREREQ")

                }
            } 
        }
        stage('04. Inband Setup'){
            when { expression { env.STAGE_04_INBAND == "yes" } }
            steps{
                script{
                    
                    info("Running '04. Inband Setup' Stage")

                     runAnsible("04_INBAND")

                }
            } 
            
        }
        stage('05. Disable Cross Connect'){
            when { expression { env.STAGE_05_CC_DISABLE == "yes" } }
            steps{
                script{
                    
                    info("Running '05. Disable Cross Connect' Stage")

                     runAnsible("05_CC_DISABLE")

                }
            } 
        }
        stage('06. Deploy Cortx Stack'){
            when { expression { env.STAGE_06_DEPLOY == "yes" } }
            steps{
                script{
                    
                    info("Running '06. Deploy Cortx Stack' Stage")

                    runAnsible("06_DEPLOY")

                }
            } 
        }
        stage('07. Enable Cross Connect'){
            when { expression { env.STAGE_07_CC_ENABLE == "yes" } }
            steps{
                script{
                    
                    info("Running '07. Enable Cross Connect' Stage")

                    runAnsible("07_CC_ENABLE")

                }
            } 
        }
        stage('08. Check PCS Status'){
            when { expression { true } }
            steps{
                script{
                    
                    info("Running '08. Check PCS Status' Stage")

                    try {

                        def remoteHost = getTestMachine("${NODE1_HOST}","${NODE_USER}","${NODE_PASS}")

                        def pcs_status = sshCommand remote: remoteHost, command: """
                            pcs status
                        """

                        def service_status = sshCommand remote: remoteHost, command: '''
                            for service in hare-consul-agent-* hare-hax-* chronyd firewalld slapd haproxy kibana elasticsearch statsd rabbitmq-server rsyslog corosync pacemaker  pcsd s3authserver csm_agent csm_web sspl-ll lnet;do
                                echo "-----------";
                                echo "$service";
                                echo "-----------";  
                                timeout 5s salt '*' service.status $service; 
                                sleep 2;
                            done
                        '''

                        writeFile(file: 'pcs_status.log', text: pcs_status)
                        writeFile(file: 'service_status.log', text: service_status)

                    } catch (err) {
                        echo err.getMessage()
                    }   

                }
            } 
        }
        stage('09. Check Service Status'){
            when { expression { true } }
            steps{
                script{

                    info("Running '09. Check Service Status' Stage")

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
                
                try {
                    sh label: 'download_log_files', returnStdout: true, script: """ 
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/log/seagate/provisioner/*.log .
                    """
                } catch (err) {
                    echo err.getMessage()
                }
                
                // Add Summary
                if (fileExists('pcs_status.log')) {
                    pcs_status=readFile(file: 'pcs_status.log')
                    if(pcs_status.contains("Failed Resource") || pcs_status.contains("Stopped")){
                        manager.buildFailure()
                        MESSAGE="Cortx Stack Deployment Failed"
                        ICON="error.gif"
                        STATUS="FAILURE"
                    }else if(pcs_status.contains("71 resources configured") && pcs_status.contains("2 nodes configured")){
                        MESSAGE="Cortx Stack Deployment Success"
                        ICON="accept.gif"
                        STATUS="SUCCESS"
                    }else{
                        manager.buildUnstable()
                        MESSAGE="Cortx Stack Deployment Status Unknown"
                        ICON="warning.gif"
                        STATUS="UNKNOWN" 
                    }

                    pcs_status_html="<textarea rows=20 cols=200 readonly style='margin: 0px; height: 392px; width: 843px;'>${pcs_status}</textarea>"
                    table_summary="<table border='1' cellspacing='0' cellpadding='0' width='400' align='left'> <tr> <td align='center'>Build</td><td align='center'><a href=${CORTX_BUILD}>${build_id}</a></td></tr><tr> <td align='center'>Test HW</td><td align='center'>${NODE1_HOST} ( primary )<br />${NODE2_HOST} ( secondary )</td></tr></table>"
                    manager.createSummary("${ICON}").appendText("<h3>${MESSAGE} for the build <a href=\"${CORTX_BUILD}\">${build_id}.</a></h3><p>Please check <a href=\"${CORTX_BUILD}/artifact/setup.log\">setup.log</a> for more info <br /><br /><h4>Test Details:</h4> ${table_summary} <br /><br /><br /><h4>PCS Status:${pcs_status_html}</h4> ", false, false, false, "red")
                   
                    env.build_id=build_id
                    env.status=STATUS
                    env.build_location="${CORTX_BUILD}"
                    env.host1="${NODE1_HOST}"
                    env.host2="${NODE2_HOST}"
                    env.pcs_log="${CORTX_BUILD}/artifact/pcs_status.log"
                    env.service_log="${CORTX_BUILD}/artifact/service_status.log"
                    env.setup_log="${CORTX_BUILD}/artifact/setup.log"
                    
                    emailext (
                        body: '''${SCRIPT, template="deployment-email.template"}''',
                        mimeType: 'text/html',
                        subject: "${MESSAGE} # ${build_id}",
                        to: "gowthaman.chinnathambi@seagate.com",
                        recipientProviders: [[$class: 'RequesterRecipientProvider']]
				    )

                } else {
                    manager.buildFailure()
                    echo "Deployment Failed. Please check setup logs"
                }
              
                 // Archive all log generated by Test
                archiveArtifacts artifacts: "**/*.log", onlyIfSuccessful: false, allowEmptyArchive: true 
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
                //extras: '-v',
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