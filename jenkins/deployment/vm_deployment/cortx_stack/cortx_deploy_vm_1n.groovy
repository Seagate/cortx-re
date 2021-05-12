#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            // Run deployment on mini_provisioner nodes (vm deployment nodes)
            label params.NODE1.isEmpty() ? "vm_deployment_1n && !cleanup_req" : "vm_deployment_1n_controller"
            customWorkspace "/var/jenkins/cortx_deployment_vm/${JOB_NAME}_${BUILD_NUMBER}"
        }
    }
	
    parameters {
        string(name: 'CORTX_BUILD', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx/github/main/centos-7.8.2003/last_successful_prod/', description: 'Build URL',  trim: true)
        string(name: 'NODE1', defaultValue: '', description: 'Node 1 Host FQDN',  trim: true)
        string(name: 'NODE_PASS', defaultValue: '', description: 'Host machine root user password',  trim: true)
        booleanParam(name: 'DEBUG', defaultValue: false, description: 'Select this if you want to preserve the VM temporarily for troublshooting')
    }

	environment {

        // NODE1_HOST - Env variables added in the node configurations
        build_id = getBuild("${CORTX_BUILD}")

        CORTX_BUILD = getActualBuild("${CORTX_BUILD}")

        NODE_DEFAULT_SSH_CRED =  credentials("${NODE_DEFAULT_SSH_CRED}")
        NODE_USER = "${NODE_DEFAULT_SSH_CRED_USR}"
        
        NODE_PASS = "${NODE_PASS.isEmpty() ? NODE_DEFAULT_SSH_CRED_PSW : NODE_PASS}"
        NODE1_HOST = "${NODE1.isEmpty() ? NODE1_HOST : NODE1 }"
        NODES = "${NODE1_HOST}"

        SETUP_TYPE = 'single'         
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

                    manager.addHtmlBadge("&emsp;<b>Build :</b> <a href=\"${CORTX_BUILD}\"><b>${build_id}</b></a> <br /> <b>Host :</b> <a href='${JENKINS_URL}/computer/${env.NODE_NAME}'><b>${NODE1_HOST}</b></a>")

                    sh """
                        set +x
                        echo "--------------VM DEPLOYMENT PARAMETERS -------------------"
                        echo "NODES                         = ${NODES}"
                        echo "CORTX_BUILD                   = ${CORTX_BUILD}"
                        echo "DEBUG                         = ${DEBUG}"
                        echo "-----------------------------------------------------------"
                    """
                    dir('cortx-re') {
                        checkout([$class: 'GitSCM', branches: [[name: '*/r2_vm_deployment_multinode']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/gowthamchinna/cortx-re']]])                
                    }

                    if( NODE1.isEmpty() ) {
					    markNodeforCleanup()
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

        stage('01. Deploy Prereq') {
            steps {
                script {
                    
                    info("Running '01. Deploy Prereq' Stage")

                    runAnsible("01_DEPLOY_PREREQ")
                }
            } 
        }

        stage('02.1 Bootstarp Provisioner') {
            steps {
                script {
                    
                    info("Running '02.1 Bootstarp Provisioner' Stage")

                    runAnsible("02_1_PRVSNR_BOOTSTRAP,02_DEPLOY_VALIDATE")
                }
            } 
        }

        stage('02.2 Platform Setup') {
            steps {
                script {
                    
                    info("Running '02.2 Platform Setup' Stage")

                    runAnsible("02_2_PLATFORM_SETUP, 02_DEPLOY_VALIDATE")
                }
            } 
        }

        stage('02.3 3party Tools Setup') {
            steps {
                script {
                    
                    info("Running '02.3 Prereq/Configuration 3party tools")

                    runAnsible("02_3_PREREQ,02_DEPLOY_VALIDATE")
                }
            } 
        }

        stage('02.4 Cortx Utils Setup') {
            steps {
                script {
                    
                    info("Running '02.4 Cortx Utils Setup' Stage")

                    runAnsible("02_4_UTILS, 02_DEPLOY_VALIDATE")
                }
            } 
        }

        stage('02.5 IO Path Setup') {
            steps {
                script {
                    
                    info("Running '02.5 IO Path Setup' Stage")

                    runAnsible("02_5_IO_PATH,02_DEPLOY_VALIDATE")
                }
            } 
        }

        stage('02.6 Control Path Setup') {
            steps {
                script {
                    
                    info("Running '02.6 Control Path Setup' Stage")

                    runAnsible("02_DEPLOY_VALIDATE, 02_6_CONTROL_PATH")
                }
            } 
        }

        stage('02.7 HA Setup') {
            steps {
                script {
                    
                    info("Running '02.7 HA Setup' Stage")

                    runAnsible("02_DEPLOY_VALIDATE,02_7_HA")
                }
            } 
        }

        stage('03. Validate') {
            steps {
                script {
                    
                    info("Running '03. Validate Deployment' Stage")

                    runAnsible("03_VALIDATE")
                }
            } 
        }
	}

    post { 
        always {
            script {
                
                // Download Log files from Deployment Machine
                try {
                    sh label: 'download_log_files', returnStdout: true, script: """ 
                        mkdir -p artifacts/srvnode1 
                        mkdir -p artifacts/srvnode2 
                        mkdir -p artifacts/srvnode3 
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/log/seagate/cortx/ha artifacts/srvnode1 &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/log/cluster artifacts/srvnode1 &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/log/pacemaker.log artifacts/srvnode1 &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/log/pcsd/pcsd.log artifacts/srvnode1 &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/log/seagate/provisioner artifacts/srvnode1 &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/opt/seagate/cortx_configs/provisioner_cluster.json artifacts/srvnode1 &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/lib/hare/cluster.yaml artifacts/srvnode1 &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/root/cortx_deployment artifacts/srvnode1 &>/dev/null || true
                    """
                } catch (err) {
                    echo err.getMessage()
                }

                archiveArtifacts artifacts: "artifacts/**/*.*", onlyIfSuccessful: false, allowEmptyArchive: true 

                if(NODE1.isEmpty()) {
                    if( params.DEBUG ) {  
                        // Take Node offline for debugging  
                        markNodeOffline("R2 - 1N VM Deployment Debug Mode Enabled on This Host - ${BUILD_URL}")
                    } else {
                        // Trigger cleanup VM
                        build job: 'Cortx-Automation/Deployment/VM-Cleanup', wait: false, parameters: [string(name: 'NODE_LABEL', value: "${env.NODE_NAME}")] 
                    }
                }
                
                // Assume Deployment Status Based on log results
                hctlStatus = ""
                if ( fileExists('artifacts/srvnode1/cortx_deployment/log/hctl_status.log') && currentBuild.currentResult == "SUCCESS" ) {
                    hctlStatus = readFile(file: 'artifacts/srvnode1/cortx_deployment/log/hctl_status.log')
                    MESSAGE = "Single Node Cortx Stack VM Deployment Success for the build ${build_id}"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                } else {
                    manager.buildFailure()
                    MESSAGE = "Single Node Cortx Stack VM Deployment Failed for the build ${build_id}"
                    ICON = "error.gif"
                    STATUS = "FAILURE"

                    // Failure component name and Cause can be retrived from deployment status log
                    if (fileExists('artifacts/srvnode1/cortx_deployment/log/deployment_status.log')){
                        try {
                           
                            // Failed Stage
                            failed_component_stage = readFile(file: 'artifacts/srvnode1/cortx_deployment/log/failed_component.log').trim()
                            
                            // Failed Component from Failed Stage
                            failed_component_name = getFailedComponentName(failed_component_stage.trim())

                            // Short Failure Log
                            deployment_status = readFile(file: 'artifacts/srvnode1/cortx_deployment/log/deployment_status.log').trim()
                            env.failure_cause = deployment_status

                            MESSAGE = "Single Node Cortx Stack VM-Deployment Failed in ${failed_component_name} for the build ${build_id}"

                            manager.addHtmlBadge("<br /> <b>Status :</b> <a href='${BUILD_URL}/artifact/artifacts/srvnode1/cortx_deployment/log/deployment_status.log'><b>Failed in '${failed_component_name}'</a>")

                        } catch (err) {
                            echo err.getMessage()
                        }
                    }
                }

                // This env vars used in email templates to get the log path
                if (fileExists('artifacts/srvnode1/cortx_deployment/log/setup.log')){
                    env.setup_log = "${BUILD_URL}/artifact/artifacts/srvnode1/cortx_deployment/log/setup.log"
                }
                if (fileExists('artifacts/srvnode1/cortx_deployment/log/hctl_status.log')){
                    env.cluster_status = "${BUILD_URL}/artifact/artifacts/srvnode1/cortx_deployment/log/hctl_status.log"
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
                
                emailext (
                    body: '''${SCRIPT, template="vm-deployment-email.template"}''',
                    mimeType: 'text/html',
                    subject: "${MESSAGE}",
                    to: "gowthaman.chinnathambi@seagate.com",
                    recipientProviders: [[$class: 'RequesterRecipientProvider']]
                )

                 // Archive all log generated by Test
                cleanWs()
            }
        }
    }
}	


// Run Ansible playbook to perform deployment
def runAnsible(tags) {
    
    dir("cortx-re/scripts/deployment") {
        ansiblePlaybook(
            playbook: 'cortx_deploy_vm.yml',
            inventory: 'inventories/vm_deployment/hosts_srvnodes',
            tags: "${tags}",
            extraVars: [
                "HOST"          : [value: "${NODES}", hidden: false],
                "CORTX_BUILD"   : [value: "${CORTX_BUILD}", hidden: false] ,
                "CLUSTER_PASS"  : [value: "${NODE_PASS}", hidden: false],
                "SETUP_TYPE"    : [value: "${SETUP_TYPE}", hidden: false],
            ],
            extras: '-v',
            colorized: true
        )
    }
}


// Get build id from build url
def getBuild(buildURL) {

    buildID = sh(script: "curl -s  $buildURL/RELEASE.INFO  | grep BUILD | cut -d':' -f2 | tr -d '\"' | xargs", returnStdout: true).trim()
    buildbranch = "Build"
    if( buildURL.contains("/cortx/github/main/") ) {
        buildbranch="Main"
    }else if( buildURL.contains("/cortx/github/stable/") ) {
        buildbranch="Stable"
    }else if ( buildURL.contains("/cortx/github/integration-custom-ci/")){
        buildbranch="Custom-CI"
    }

 return "$buildbranch#$buildID"   
}

// Get build id from build url
def getActualBuild(buildURL) {

    buildRoot = "http://cortx-storage.colo.seagate.com/releases/cortx/github"
    buildID = sh(script: "curl -s  $buildURL/RELEASE.INFO  | grep BUILD | cut -d':' -f2 | tr -d '\"' | xargs", returnStdout: true).trim()
    buildbranch = "Build"
    if( buildURL.contains("/cortx/github/main/") ) {
        buildURL="${buildRoot}/main/centos-7.8.2003/${buildID}/prod"
    }else if( buildURL.contains("/cortx/github/stable/") ) {
        buildURL="${buildRoot}/main/centos-7.8.2003/${buildID}/prod"
    }else if ( buildURL.contains("/cortx/github/integration-custom-ci/")){
        buildURL="${buildRoot}/integration-custom-ci/centos-7.8.2003/${buildID}"
    }

 return buildURL  
}


def getFailedComponentName(String failedStage){
    
    component = "RE"
    if (failedStage.contains('components.system') || failedStage.contains('components.misc_pkgs') || failedStage.contains('bootstrap')){
        component = "Provisioner"
    } else if(failedStage.contains('components.motr')){
        component = "Motr"
    } else if(failedStage.contains('components.s3server')){
        component = "S3Server"
    } else if(failedStage.contains('components.hare')){
        component = "hare"
    } else if(failedStage.contains('components.ha')){
        component = "HA"
    } else if(failedStage.contains('components.sspl')){
        component = "Monitor"
    } else if(failedStage.contains('components.csm')){
        component = "CSM"
    } else if(failedStage.contains('components.cortx_utils')){
        component = "Foundation"
    }

    return component
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

// Make failed node offline
def markNodeOffline(message) {
    node = getCurrentNode(env.NODE_NAME)
    computer = node.toComputer()
    computer.setTemporarilyOffline(true)
    computer.doChangeOfflineCause(message)
    computer = null
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

// Add 'cleanup_req' label to VM to identify unclean vm
def markNodeforCleanup() {
	nodeLabel = "cleanup_req"
    node = getCurrentNode(env.NODE_NAME)
	node.setLabelString(node.getLabelString() + " " + nodeLabel)
	node.save()
    node = null
}