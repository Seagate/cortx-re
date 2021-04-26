#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            // Run deployment on mini_provisioner nodes (vm deployment nodes)
            label params.HOST == "-" ? "vm_deployment_1n && !cleanup_req" : "vm_deployment_1n_user_host"
            customWorkspace "/var/jenkins/mini_provisioner/${JOB_NAME}_${BUILD_NUMBER}"
        }
    }
	
    parameters {
        string(name: 'CORTX_BUILD', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx/github/stable/centos-7.8.2003/136/prod/', description: 'Build URL',  trim: true)
        string(name: 'HOST', defaultValue: '-', description: 'Host FQDN',  trim: true)
        password(name: 'HOST_PASS', defaultValue: '-', description: 'Host machine root user password')
        choice(name: 'NOTIFICATION', choices: ["DevOps.RE", "None", "Ujwal", "MGMT"], description: 'Notification group ')
        booleanParam(name: 'DEBUG', defaultValue: false, description: 'Select this if you want to preserve the VM temporarily for troublshooting')
        booleanParam(name: 'CREATE_JIRA_ISSUE_ON_FAILURE', defaultValue: false, description: 'Select this if you want to create Jira issue on failure')
        booleanParam(name: 'AUTOMATED', defaultValue: false, description: 'Only for Internal RE Use')
    }
	
	triggers {
         cron('0 14 1-7 * *')
	}	 	

	environment {

        // NODE1_HOST - Env variables added in the node configurations
        build_id = getBuild("${CORTX_BUILD}")

        // GID/pwd used to update root password
        NODE_UN_PASS_CRED_ID = "mini-prov-change-pass"

        NODE_DEFAULT_SSH_CRED =  credentials("${NODE_DEFAULT_SSH_CRED}")
        NODE_USER = "${NODE_DEFAULT_SSH_CRED_USR}"
        NODE1_HOST = "${HOST == '-' ? NODE1_HOST : HOST }"
        NODE_PASS = "${HOST_PASS == '-' ? NODE_DEFAULT_SSH_CRED_PSW : HOST_PASS}"

        STAGE_00_PREPARE = "yes"
        STAGE_01_DEPLOY_PREREQ = "yes"
        STAGE_02_DEPLOY = "yes"
		STAGE_03_SANITY_TEST = "yes"

    }

    options {
        timeout(time: 180, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        buildDiscarder(logRotator(numToKeepStr: "30"))
    }

    stages {

        stage ('Prerequisite') {
            steps {
                script {
				
                    if ( "${HOST}" == "-" ) {
					    markNodeforCleanup()
                    }
                    
                    manager.addHtmlBadge("&emsp;<b>Build :</b> <a href=\"${CORTX_BUILD}\"><b>${build_id}</b></a> <br /> <b>Host :</b> <a href='${JENKINS_URL}/computer/${env.NODE_NAME}'><b>${NODE1_HOST}</b></a>")

                    env.CHANGE_PASS = env.CHANGE_PASS ?: 'no'

                    sh """
                        set +x
                        echo "--------------VM DEPLOYMENT PARAMETERS -------------------"
                        echo "NODE1                         = ${NODE1_HOST}"
                        echo "CORTX_BUILD                   = ${CORTX_BUILD}"
                        echo "DEBUG                         = ${DEBUG}"
                        echo "NOTIFICATION                  = ${NOTIFICATION}"
                        echo "CREATE_JIRA_ISSUE_ON_FAILURE  = ${CREATE_JIRA_ISSUE_ON_FAILURE}"
                        echo "-----------------------------------------------------------"
                    """
                    dir('cortx-re') {
                        checkout([$class: 'GitSCM', branches: [[name: '*/r2_vm_deployment_deprecated']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/gowthamchinna/cortx-re']]])                
                    }
                }
            }
        }

        stage('01. Prepare Environment') {
            when { expression { env.STAGE_00_PREPARE == "yes" } }
            steps {
                script {
                    
                    info("Running '01. Prepare Environment' Stage")  

                    runAnsible("00_PREPARE")
                }
            }
        }

        stage('02. Deploy Prereq') {
            when { expression { env.STAGE_01_DEPLOY_PREREQ == "yes" } }
            steps {
                script {
                    
                    info("Running '02. Deploy Prereq' Stage")

                    runAnsible("01_DEPLOY_PREREQ")
                }
            } 
        }

        stage('03. Deploy Cortx Stack') {
            when { expression { env.STAGE_02_DEPLOY == "yes" } }
            steps {
                script {
                    
                    info("Running '03. Deploy Cortx Stack' Stage")

                    runAnsible("02_DEPLOY")
                }
            } 
        }
		
		stage ('04. Sanity Test') {
			when { expression { env.STAGE_03_SANITY_TEST == "yes" } }
			steps {
				script {
				catchError(stageResult: 'FAILURE') {
				build job: 'Automation/QA-Auto-Devops-Sanity-Pipeline', wait: true, propagate: false, parameters: [string(name: 'HOSTNAME', value: "${NODE1_HOST}"), string(name: 'HOST_PASS', value: "${NODE_PASS}"), string(name: 'CORTX_BUILD', value: "${build_id}")]
				}
				
				copyArtifacts filter: 'results.xml', fingerprintArtifacts: true, flatten: true, optional: true, projectName: 'Automation/QA-Auto-Devops-Sanity-Pipeline', selector: lastCompleted(), target: ''				
				 
				}
			}
		}	
	}
	
	
	
    post { 
        always {
            script {
			
                junit allowEmptyResults: true, testResults: 'results.xml'
                // Download Log files from Deployment Machine
                try {
                    sh label: 'download_log_files', returnStdout: true, script: """ 
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/log/seagate/provisioner/*.log . &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/opt/seagate/cortx_configs/provisioner_cluster.json . &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/root/config.ini . &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/root/*.log . &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/lib/hare/cluster.yaml . &>/dev/null || true
                    """
                } catch (err) {
                    echo err.getMessage()
                }

                // Cleanup VM Based on 'DEBUG' & 'HOST' values
                if ( "${HOST}" == "-" ) {
                    
                    if ( params.DEBUG ) {  
                        // Take Node offline for debugging  
                        markNodeOffline("R2 VM Deployment Debug Mode Enabled on This Host - ${BUILD_URL}")
                    } else {
                        // Trigger cleanup VM
                        build job: 'Cortx-Automation/Deployment/VM-Cleanup', wait: false, parameters: [string(name: 'NODE_LABEL', value: "${env.NODE_NAME}")]                    
                    }
                }
                
                // Assume Deployment Status Based on log results
                hctlStatus = ""
                if ( fileExists('hctl_status.log') && currentBuild.currentResult == "SUCCESS" ) {
                    hctlStatus = readFile(file: 'hctl_status.log')
                    MESSAGE = "Cortx Stack VM Deployment Success for the build ${build_id}"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                } else {
                    manager.buildFailure()
                    MESSAGE = "Cortx Stack VM Deployment Failed for the build ${build_id}"
                    ICON = "error.gif"
                    STATUS = "FAILURE"

                    // Failure component name and Cause can be retrived from deployment status log
                    if (fileExists('deployment_status.log')) {
                        try {
                           
                            // Failed Stage
                            failed_component_stage = readFile(file: 'failed_component.log').trim()
                            
                            // Failed Component from Failed Stage
                            failed_component_name = getFailedComponentName(failed_component_stage.trim())

                            // Short Failure Log
                            deployment_status = readFile(file: 'deployment_status.log').trim()
                            env.failure_cause = deployment_status

                            MESSAGE = "Cortx Stack VM-Deployment Failed in ${failed_component_name} for the build ${build_id}"

                            echo "Previous Job build status : ${currentBuild.previousBuild.result}"

                            // Create JIRA if deployment failed and create Jira true
                            // 1. Jira issue should be created only when 'CREATE_JIRA_ISSUE_ON_FAILURE' option is enabled
                            // 2. Jira issue should be created only when 'previous build is success' (To avoid Multiple jira tickets)
                            // FIXME - LOGIC NEED TO BE IMPROVED TO QUERY JIRA TO IDENTIFY EXSITING TICKETS FOR THE SAME ISSUE
                            if ( params.CREATE_JIRA_ISSUE_ON_FAILURE && ( !params.AUTOMATED || "SUCCESS".equals(currentBuild.previousBuild.result))) {
                                
                                jiraIssue = createJiraIssue(failed_component_stage.trim(), failed_component_name, deployment_status.trim())

                                manager.addHtmlBadge(" <br /><b>Jira Issue :</b> <a href='https://jts.seagate.com/browse/${jiraIssue}'><b>${jiraIssue}</b></a>")

                                env.jira_issue="https://jts.seagate.com/browse/${jiraIssue}"
                            }

                        } catch (err) {
                            echo err.getMessage()
                        }
                    }
                }

                // This env vars used in email templates to get the log path
                if (fileExists('setup.log')) {
                    env.setup_log = "${BUILD_URL}/artifact/setup.log"
                }
                if (fileExists('hctl_status.log')) {
                    env.cluster_status = "${BUILD_URL}/artifact/hctl_status.log"
                }

                // Create Jenkins Summary page with deployment info
                hctlStatusHTML = "<textarea rows=20 cols=200 readonly style='margin: 0px; height: 392px; width: 843px;'>${hctlStatus}</textarea>"
                tableSummary = "<table border='1' cellspacing='0' cellpadding='0' width='400' align='left'> <tr> <td align='center'>Build</td><td align='center'><a href=${CORTX_BUILD}>${build_id}</a></td></tr><tr> <td align='center'>Test VM</td><td align='center'><a href='${JENKINS_URL}/computer/${env.NODE_NAME}'><b>${NODE1_HOST}</b></a></td></tr></table>"
                manager.createSummary("${ICON}").appendText("<h3>Cortx Stack VM-Deployment ${currentBuild.currentResult} for the build <a href=\"${CORTX_BUILD}\">${build_id}.</a></h3><p>Please check <a href=\"${BUILD_URL}/artifact/setup.log\">setup.log</a> for more info <br /><br /><h4>Test Details:</h4> ${tableSummary} <br /><br /><br /><h4>Cluster Status:${hctlStatusHTML}</h4> ", false, false, false, "red")
                     
                // Send Email about deployment status
                env.build_id = build_id
                env.build_location = "${CORTX_BUILD}"
                env.host = "${NODE1_HOST}"
                env.deployment_status = "${MESSAGE}"

                if ( "FAILURE".equals(currentBuild.currentResult) && params.AUTOMATED ) {
                    toEmail = getNotificationList("${NOTIFICATION}")
                    toEmail = "${toEmail}, priyank.p.dalal@seagate.com, shailesh.vaidya@seagate.com, mukul.malhotra@seagate.com"
                } else {
                    toEmail = getNotificationList("${NOTIFICATION}")
                }
				
                
                
				emailext (
					
                    body: '''${SCRIPT, template="vm-deployment-email.template"}${SCRIPT, template="REL_QA_SANITY_CUS_EMAIL_2.template"}''',
					mimeType: 'text/html',
                    subject: "${MESSAGE}",
                    to: toEmail,
					recipientProviders: [[$class: 'RequesterRecipientProvider']]
                )

                 // Archive all log generated by Test
                archiveArtifacts artifacts: "*.log, *.json, *.ini, *.yaml, *.xml", onlyIfSuccessful: false, allowEmptyArchive: true 
                cleanWs()
            }
        }
    }
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


// Run Ansible playbook to perform deployment
def runAnsible(tags) {
    withCredentials([usernamePassword(credentialsId: "${NODE_UN_PASS_CRED_ID}", passwordVariable: 'SERVICE_PASS', usernameVariable: 'SERVICE_USER')]) {
        
        dir("cortx-re/scripts/deployment") {
            ansiblePlaybook(
                playbook: 'cortx_deploy_vm_1node.yml',
                inventory: 'inventories/vm_deployment/hosts_1node',
                tags: "${tags}",
                extraVars: [
                    "NODE1"                 : [value: "${NODE1_HOST}", hidden: false],
                    "BUILD_URL"             : [value: "${CORTX_BUILD}", hidden: false] ,
                    "CLUSTER_PASS"          : [value: "${NODE_PASS}", hidden: false],
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

// Add 'cleanup_req' label to VM to identify unclean vm
def markNodeforCleanup() {
	nodeLabel = "cleanup_req"
    node = getCurrentNode(env.NODE_NAME)
	node.setLabelString(node.getLabelString() + " " + nodeLabel)
	node.save()
    node = null
}

// Get current Node
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

// Get build id from build url
def getBuild(buildURL) {

    buildID = sh(script: "curl -s  $buildURL/RELEASE.INFO  | grep BUILD | cut -d':' -f2 | tr -d '\"' | xargs", returnStdout: true).trim()
    buildbranch = "Build"
    if ( buildURL.contains("/cortx/github/main/") ) {
        buildbranch="Main"
    }else if ( buildURL.contains("/cortx/github/stable/") ) {
        buildbranch="Stable"
    }else if ( buildURL.contains("/cortx/github/integration-custom-ci/")) {
        buildbranch="Custom-CI"
    }

 return "$buildbranch#$buildID"   
}

// Get email notification list based on argument
def getNotificationList(String notificationType) {

    toEmail=""
    if ( notificationType == "DevOps.RE" ) {
        toEmail = "CORTX.DevOps.RE@seagate.com"
    } else if ( notificationType == "Ujwal" ) {
        toEmail = "ujjwal.lanjewar@seagate.com, amit.kapil@seagate.com, priyank.p.dalal@seagate.com,amol.j.kongre@seagate.com,gowthaman.chinnathambi@seagate.com"
    } else if ( notificationType == "MGMT" ) {
        toEmail = "manoj.management.team@seagate.com, cortx.sme@seagate.com"
    } else {
        toEmail = "gowthaman.chinnathambi@seagate.com"
    }

    //toEmail = "gowthaman.chinnathambi@seagate.com"
    return toEmail
}

// Create jira issues on failure and input parameter 
def createJiraIssue(String failedStage, String failedComponent, String failureLog) {

    def issue = [
                    fields: [ 
                        project: [key: 'EOS'],
                        issuetype: [name: 'Bug'],
                        priority: [name: "Blocker"],
                        versions: [[name: "LDR-R2"]],
                        labels: ["PI-1"],
                        components: [[name: "${failedComponent}"]],
                        summary: "VM-Deployment Failed in ${failedComponent} for the build ${build_id}",
                        description: "{panel}VM Deployment is failed in ${failedStage} for the build [${build_id}|${CORTX_BUILD}]. Please check Jenkins console log and deployment log for more info.\n"+
                                    "\n h4. Deployment Info \n"+
                                    "|Cortx build|[${build_id}|${CORTX_BUILD}]|\n"+
                                    "|Jenkins build|[${JOB_BASE_NAME}#${BUILD_NUMBER} |${BUILD_URL}]|\n"+
                                    "|Failed Component |*${failedComponent}*|\n"+
                                    "|Deployment Host|${NODE1_HOST}|\n"+
                                    "|Deployment Log|[${JOB_BASE_NAME}/${BUILD_NUMBER}/artifact|${BUILD_URL}artifact]|\n"+
                                    "\n\n"+
                                    "h4. Failure Log\n"+
                                    "{code:java}${failureLog}{code} \n {panel}"
                    ]
                ]

  def newIssue = jiraNewIssue issue: issue, site: 'SEAGATE_JIRA'
  return newIssue.data.key
}

def getFailedComponentName(String failedStage) {
    
    component = "RE"
    if (failedStage.contains('components.system') || failedStage.contains('components.misc_pkgs') || failedStage.contains('bootstrap')) {
        component = "Provisioner"
    } else if(failedStage.contains('components.motr')) {
        component = "Motr"
    } else if(failedStage.contains('components.s3server')) {
        component = "S3Server"
    } else if(failedStage.contains('components.hare')) {
        component = "hare"
    } else if(failedStage.contains('components.ha')) {
        component = "HA"
    } else if(failedStage.contains('components.sspl')) {
        component = "Monitor"
    } else if(failedStage.contains('components.csm')) {
        component = "CSM"
    }

    return component
}
