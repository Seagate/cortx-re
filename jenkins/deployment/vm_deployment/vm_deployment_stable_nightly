#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            // Run deployment on mini_provisioner nodes (vm deployment nodes)
            label "mini_provisioner && !cleanup_req"
        }
    }
	
    parameters {
        string(name: 'CORTX_BUILD', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx/github/stable/centos-7.8.2003/last_successful_prod/', description: 'Build URL')
        choice(name: 'DEBUG', choices: ["no", "yes"], description: 'Keep Host for Debuging') 
    }
	triggers {
         cron('H 2 * * *')
    }
	environment {

        // NODE1_HOST - Env variables added in the node configurations
        build_id = sh(script: "echo ${CORTX_BUILD} | rev | cut -d '/' -f2,3 | rev", returnStdout: true).trim()

        // GID/pwd used to update root password
        NODE_UN_PASS_CRED_ID = "mini-prov-change-pass"

        NODE_DEFAULT_SSH_CRED  = credentials("${NODE_DEFAULT_SSH_CRED}")
        NODE_USER = "${NODE_DEFAULT_SSH_CRED_USR}"
        NODE_PASS = "${NODE_DEFAULT_SSH_CRED_PSW}"
        CLUSTER_PASS = "${NODE_DEFAULT_SSH_CRED_PSW}"
        

        STAGE_01_PREPARE = "yes"
        STAGE_02_DEPLOY_PREREQ = "yes"
        STAGE_03_DEPLOY = "yes"
        STAGE_04_SANITY = "no"

    }

    options {
        timeout(time: 180, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        buildDiscarder(logRotator(numToKeepStr: "30"))
        //buildDiscarder(logRotator(numToKeepStr: '1', artifactNumToKeepStr: '1'))
    }

    stages {

        stage ('Prerequisite'){
            steps{
                script{
				
					markNodeforCleanup()
                    
                    manager.addHtmlBadge("&emsp;<b>Build :</b> <a href=\"${CORTX_BUILD}\"><b>${build_id}</b></a> <br /> <b>Host :</b> <a href='${JENKINS_URL}/computer/${env.NODE_NAME}'><b>${NODE1_HOST}</b></a>")

                    if(!env.CHANGE_PASS){
                        env.CHANGE_PASS = "no"
                    }

                    sh """
                        set +x
                        echo "--------------HW DEPLOYMENT PARAMETERS -------------------"
                        echo "NODE1             = ${NODE1_HOST}"
                        echo "CORTX_BUILD       = ${CORTX_BUILD}"
                        echo "-----------------------------------------------------------"
                    """
                    dir('cortx-re'){
                        checkout([$class: 'GitSCM', branches: [[name: '*/r2_cortx_deployment_vm']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])                
                    }
                }
            }
        }
        stage('01. Prepare Environment'){
            when { expression { env.STAGE_01_PREPARE == "yes" } }
            steps{
                script{
                    
                    info("Running '01. Prepare Environment' Stage")  

                    runAnsible("01_PREPARE")
                }
            }
        }

        stage('02. Deploy Prereq'){
            when { expression { env.STAGE_02_DEPLOY_PREREQ == "yes" } }
            steps{
                script{
                    
                    info("Running '02. Deploy Prereq' Stage")

                    runAnsible("03_DEPLOY_PREREQ")

                }
            } 
        }

        stage('03. Deploy Cortx Stack'){
            when { expression { env.STAGE_03_DEPLOY == "yes" } }
            steps{
                script{
                    
                    info("Running '03. Deploy Cortx Stack' Stage")

                    runAnsible("06_DEPLOY")

                }
            } 
        }
        stage('04. Check Deployment Status'){
            when { expression { true } }
            steps{
                script{
                    
                    info("Running '04. Check HCTL Status' Stage")

                    try {

                        def remoteHost = getTestMachine("${NODE1_HOST}","${NODE_USER}","${NODE_PASS}")

                        def pcs_status = sshCommand remote: remoteHost, command: """
                            hctl status
                        """

                        def service_status = sshCommand remote: remoteHost, command: '''
                            salt '*' test.ping  
                            salt "*" service.stop puppet
                            salt "*" service.disable puppet
                            salt '*' pillar.get release  
                            salt '*' grains.get node_id  
                            salt '*' grains.get cluster_id  
                            salt '*' grains.get roles 
                        '''

                        writeFile(file: 'hctl_status.log', text: pcs_status)
                        writeFile(file: 'service_status.log', text: service_status)

                    } catch (err) {
                        echo err.getMessage()
                    }   

                }
            } 
        }
        stage('05. Run Sanity'){
            when { expression { env.STAGE_05_SANITY == "yes" } }
            steps{
                script{
                    info("Running '05. Run Sanity' Stage")

                    try {

                        def remoteHost = getTestMachine("${NODE1_HOST}","${NODE_USER}","${NODE_PASS}")

                        def csm_sanity = sshCommand remote: remoteHost, command: """
                            sleep 300
                            csm_test -f /opt/seagate/cortx/csm/test/test_data/args.yaml -t /opt/seagate/cortx/csm/test/plans/self_test.pln || true
                        """
                        
                        def s3_sanity = sshCommand remote: remoteHost, command: """
                            sleep 300
                            salt 'srvnode-1' state.apply components.s3clients || true
                            sleep 300
                            /opt/seagate/cortx/s3/scripts/s3-sanity-test.sh -e 127.0.0.1 || true
                        """
                        
                        def sspl_sanity = sshCommand remote: remoteHost, command: """
                            sleep 300
                            sh /opt/seagate/cortx/sspl/sspl_test/run_tests.sh || true
                        """

                        writeFile(file: 'csm_sanity.log', text: csm_sanity)
                        writeFile(file: 's3_sanity.log', text: s3_sanity)
                        writeFile(file: 'sspl_sanity.log', text: sspl_sanity)

                    } catch (err) {
                        echo err.getMessage()
                    } 

                }
            } 
        }
	}

    post { 
        always {
            script{
                
                try {
                    sh label: 'download_log_files', returnStdout: true, script: """ 
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/log/seagate/provisioner/*.log . || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/opt/seagate/cortx_configs/provisioner_cluster.json . || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/root/config.ini . || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/lib/hare/cluster.yaml . || true
                    """
                } catch (err) {
                    echo err.getMessage()
                }

                if("${DEBUG}" == "yes"){
                    
                    markNodeOffline("R2 VM Deployment Debug Mode Enabled on This Host  - ${JOB_URL}")

                } else {

                    // Trigger cleanup VM
                    build job: 'Cortx-Automation/Deployment/VM-Cleanup', wait: false, parameters: [string(name: 'NODE_LABEL', value: "${env.NODE_NAME}")]

                }
                
                // Add Summary
                hctl_status=""
                if (fileExists('hctl_status.log')) {
                    hctl_status=readFile(file: 'hctl_status.log')
                    MESSAGE="Cortx Stack Deployment Success"
                    ICON="accept.gif"
                    STATUS="SUCCESS"
                }else{
                    manager.buildFailure()
                    MESSAGE = "Cortx Stack Deployment Failed"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
                }

                hctl_status_html="<textarea rows=20 cols=200 readonly style='margin: 0px; height: 392px; width: 843px;'>${hctl_status}</textarea>"
                table_summary="<table border='1' cellspacing='0' cellpadding='0' width='400' align='left'> <tr> <td align='center'>Build</td><td align='center'><a href=${CORTX_BUILD}>${build_id}</a></td></tr><tr> <td align='center'>Test HW</td><td align='center'>${NODE1_HOST}</td></tr></table>"
                manager.createSummary("${ICON}").appendText("<h3>${MESSAGE} for the build <a href=\"${CORTX_BUILD}\">${build_id}.</a></h3><p>Please check <a href=\"${CORTX_BUILD}/artifact/setup.log\">setup.log</a> for more info <br /><br /><h4>Test Details:</h4> ${table_summary} <br /><br /><br /><h4>PCS Status:${hctl_status_html}</h4> ", false, false, false, "red")
              

                env.build_id = build_id
                env.status = STATUS
                env.build_location = "${CORTX_BUILD}"
                env.host1 = "${NODE1_HOST}"
                env.host2 = "-"
                env.pcs_log = "-"
                env.service_log = "-"
                env.setup_log = "${BUILD_URL}/artifact/setup.log"
                
                //toEmail = "nilesh.govande@seagate.com, yashodhan.pise@seagate.com,priyank.p.dalal@seagate.com,jaikumar.gidwani@seagate.com,amol.j.kongre@seagate.com,nitin.nimran@seagate.com,gowthaman.chinnathambi@seagate.com"
                //toEmail = "gowthaman.chinnathambi@seagate.com"
                toEmail = "CORTX.DevOps.RE@seagate.com"
                emailext (
                    body: '''${SCRIPT, template="deployment-email.template"}''',
                    mimeType: 'text/html',
                    subject: "${MESSAGE} # ${build_id}",
                    to: toEmail,
                    recipientProviders: [[$class: 'RequesterRecipientProvider']]
                )

                 // Archive all log generated by Test
                archiveArtifacts artifacts: "*.log, *.json, *.ini, *.yaml", onlyIfSuccessful: false, allowEmptyArchive: true 
                cleanWs()
            }
        }
    }
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
    withCredentials([usernamePassword(credentialsId: "${NODE_UN_PASS_CRED_ID}", passwordVariable: 'SERVICE_PASS', usernameVariable: 'SERVICE_USER'),string(credentialsId: "${CLOUDFORM_TOKEN_CRED_ID}", variable: 'CLOUDFORM_API_CRED')]) {
        
        dir("cortx-re/scripts/deployment"){
            ansiblePlaybook(
                playbook: 'cortx_deploy_vm_1node.yml',
                inventory: 'inventories/vm_deployment/hosts_1node',
                tags: "${tags}",
                extraVars: [
                    "REIMAGE"               : [value: "no", hidden: false],
                    "NODE1"                 : [value: "${NODE1_HOST}", hidden: false],
                    "BUILD_URL"             : [value: "${CORTX_BUILD}", hidden: false] ,
                    "CLUSTER_PASS"          : [value: "${CLUSTER_PASS}", hidden: false],
                    "CLOUDFORM_API_CRED"    : [value: "${CLOUDFORM_API_CRED}", hidden: true],
                    "SERVICE_USER"          : [value: "${SERVICE_USER}", hidden: true],
                    "SERVICE_PASS"          : [value: "${SERVICE_PASS}", hidden: true],
                    "CHANGE_PASS"           : [value: "${CHANGE_PASS}", hidden: false]
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


def markNodeforCleanup() {
	nodeLabel = "cleanup_req"
    node = getCurrentNode(env.NODE_NAME)
	node.setLabelString(node.getLabelString()+" "+nodeLabel)
	node.save()
    node = null
}

def getCurrentNode(nodeName) {
  for (node in Jenkins.instance.nodes) {
      if (node.getNodeName() == nodeName) {
        echo "Found node for $nodeName"
        return node
    }
  }
  throw new Exception("No node for $nodeName")
}

// Make failed node offline
def markNodeOffline(message) {
    node = getCurrentNode(env.NODE_NAME)
    computer = node.toComputer()
    computer.setTemporarilyOffline(true)
    computer.doChangeOfflineCause(message)
    computer = null
    node = null
}
