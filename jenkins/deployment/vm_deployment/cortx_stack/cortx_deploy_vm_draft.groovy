#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            // Run deployment on mini_provisioner nodes (vm deployment nodes)
            label "vm_deployment_3n_controller"
            customWorkspace "/var/jenkins/cortx_deployment_vm/${JOB_NAME}_${BUILD_NUMBER}"
        }
    }
	
    parameters {
        string(name: 'CORTX_BUILD', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx/github/main/centos-7.8.2003/last_successful_prod/', description: 'Build URL',  trim: true)
        string(name: 'NODE1', defaultValue: '', description: 'Node 1 Host FQDN',  trim: true)
        string(name: 'NODE2', defaultValue: '', description: 'Node 2 Host FQDN',  trim: true)
        string(name: 'NODE3', defaultValue: '', description: 'Node 3 Host FQDN',  trim: true)
        string(name: 'NODE_PASS', defaultValue: '-', description: 'Host machine root user password',  trim: true)
        booleanParam(name: 'DEBUG', defaultValue: false, description: 'Select this if you want to preserve the VM temporarily for troublshooting')
    }

	environment {

        // NODE1_HOST - Env variables added in the node configurations
        build_id = getBuild("${CORTX_BUILD}")

        NODE_DEFAULT_SSH_CRED =  credentials("${NODE_DEFAULT_SSH_CRED}")
        NODE_USER = "${NODE_DEFAULT_SSH_CRED_USR}"
        NODE_PASS = "${NODE_DEFAULT_SSH_CRED_PSW}"

        DEPLOYMENT_NODE_COUNT = 3
        DEPLOYMENT_NODE_LABEL = "vm_deployment_3n"
        SETUP_TYPE = '3_node' 

        STAGE_00_PREPARE = "yes"
        STAGE_01_DEPLOY_PREREQ = "yes"
        STAGE_02_DEPLOY = "yes"
        
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
				
                    if(NODE1.isEmpty() && NODE2.isEmpty() && NODE3.isEmpty() && NODE_PASS.isEmpty()){  
                        NODES = getDeploymentNode(env.DEPLOYMENT_NODE_LABEL, env.DEPLOYMENT_NODE_COUNT)
                    }else if(!NODE1.isEmpty() && !NODE2.isEmpty() && !NODE3.isEmpty() && !NODE_PASS.isEmpty()){  
                        NODES = "${NODE1},${NODE2},${NODE3}"
                        NODE_PASS = param.NODE_PASS
                    }else{
                        error("Invalid Host & Host Pass Input")
                    }
                    
                    manager.addHtmlBadge("&emsp;<b>Build :</b> <a href=\"${CORTX_BUILD}\"><b>${build_id}</b></a> <br /> <b>Host :</b> <a href='${JENKINS_URL}/computer/${env.NODE_NAME}'><b>${NODE1_HOST}</b></a>")

                    sh """
                        echo "--------------VM DEPLOYMENT PARAMETERS -------------------"
                        echo "NODES                         = ${NODES}"
                        echo "CORTX_BUILD                   = ${CORTX_BUILD}"
                        echo "DEBUG                         = ${DEBUG}"
                        echo "-----------------------------------------------------------"
                    """
                    dir('cortx-re') {
                        checkout([$class: 'GitSCM', branches: [[name: '*/r2_vm_deployment']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])                
                    }

                    error("Good")
                }
            }
        }

        stage('00. Prepare Environment') {
            when { expression { env.STAGE_00_PREPARE == "yes" } }
            steps {
                script {
                    
                    info("Running '00. Prepare Environment' Stage")  

                    runAnsible("00_PREPARE")
                }
            }
        }

        stage('01. Deploy Prereq') {
            when { expression { env.STAGE_01_DEPLOY_PREREQ == "yes" } }
            steps {
                script {
                    
                    info("Running '01. Deploy Prereq' Stage")

                    runAnsible("01_DEPLOY_PREREQ")
                }
            } 
        }

        stage('02. Deploy Cortx Stack') {
            when { expression { env.STAGE_02_DEPLOY == "yes" } }
            steps {
                script {
                    
                    info("Running '02. Deploy Cortx Stack' Stage")

                    runAnsible("02_DEPLOY")
                }
            } 
        }

        stage('03. Validate') {
            when { expression { env.STAGE_03_VALIDATE == "yes" } }
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
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/log/seagate/provisioner/*.log . &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/opt/seagate/cortx_configs/provisioner_cluster.json . &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/root/cortx_deployment/* . &>/dev/null || true
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/var/lib/hare/cluster.yaml . &>/dev/null || true
                    """
                } catch (err) {
                    echo err.getMessage()
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
                    if (fileExists('deployment_status.log')){
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

                        } catch (err) {
                            echo err.getMessage()
                        }
                    }
                }

                // This env vars used in email templates to get the log path
                if (fileExists('setup.log')){
                    env.setup_log = "${BUILD_URL}/artifact/setup.log"
                }
                if (fileExists('hctl_status.log')){
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
                
                emailext (
                    body: '''${SCRIPT, template="vm-deployment-email.template"}''',
                    mimeType: 'text/html',
                    subject: "${MESSAGE}",
                    to: "gowthaman.chinnathambi@seagate.com",
                    recipientProviders: [[$class: 'RequesterRecipientProvider']]
                )

                 // Archive all log generated by Test
                archiveArtifacts artifacts: "*.log, *.json, *.ini, *.yaml", onlyIfSuccessful: false, allowEmptyArchive: true 
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
                "NODE"          : [value: "${NODES}", hidden: false],
                "BUILD_URL"     : [value: "${CORTX_BUILD}", hidden: false] ,
                "CLUSTER_PASS"  : [value: "${NODE_PASS}", hidden: false],
                "SETUP_TYPE"    : [value: "${SETUP_TYPE}", hidden: false],
            ],
            extras: '-v',
            colorized: true
        )
    }
}

// Get current Node

def getDeploymentNode(node_label, count, counter) {

    for (node in Jenkins.instance.nodes) {

        computer = node.toComputer()
        node_label = node.getLabelString()

        if ( !computer.isTemporarilyOffline() && computer.countBusy()==0 
                && !node_label.contains("cleanup_req") && ( node_label.contains(node_label) ){
            counter = counter+1
            echo "Node-${counter} : Reserving Node $nodeName for Deployment"
            
            node="${nodeName}"
            if(count > = 3){
                break
            }
        }

        if(count <  3){
            sleep 60
            getDeploymentNode(node_label, count, counter)
        }
        
    }

    return 
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