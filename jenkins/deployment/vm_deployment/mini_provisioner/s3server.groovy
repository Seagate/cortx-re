#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            // Run deployment on mini_provisioner nodes (vm deployment nodes)
            label "mini_provisioner"
        }
    }
	
    parameters {
        string(name: 'CORTX_BUILD', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/release/centos-7.8.2003/custom-build-430/', description: 'Build URL',  trim: true)
        choice(name: 'REIMAGE', choices: [ "yes", "no" ], description: 'Re-Image option')
    }

	environment {

        // NODE1_HOST - Env variables added in the node configurations
        build_id = sh(script: "echo ${CORTX_BUILD} | rev | cut -d '/' -f2,3 | rev", returnStdout: true).trim()

        // GID/pwd used to update root password 
        NODE_UN_PASS_CRED_ID = "736373_manageiq_up"

        // Test LDAP credential 
        LDAP_ROOT_CRED  = credentials("mini-prov-ldap-root-cred")
        LDAP_ROOT_USER = "${LDAP_ROOT_CRED_USR}"
        LDAP_ROOT_PWD = "${LDAP_ROOT_CRED_PSW}"

        // Test LDAP credential 
        LDAP_SG_CRED  = credentials("mini-prov-ldap-sg-cred")
        LDAP_SGIAM_USER = "${LDAP_SG_CRED_USR}"
        LDAP_SGIAM_PWD = "${LDAP_SG_CRED_PSW}"

        // BMC credential 
        BMC_CRED  = credentials("mini-prov-bmc-cred")
        BMC_USER = "${BMC_CRED_USR}"
        BMC_SECRET = "${BMC_CRED_PSW}"

        // Credentials used to SSH node
        NODE_DEFAULT_SSH_CRED  = credentials("vm-deployment-ssh-cred")
        NODE_USER = "${NODE_DEFAULT_SSH_CRED_USR}"
        NODE_PASS = "${NODE_DEFAULT_SSH_CRED_PSW}"
        CLUSTER_PASS = "${NODE_DEFAULT_SSH_CRED_PSW}"

        // Control to skip/run stages - (used for trublshooting purpose)
        STAGE_00_PREPARE_ENV = "yes"
        STAGE_00_REIMAGE = "${REIMAGE}"
        STAGE_01_PREREQ = "yes"
        STAGE_02_INSTALL_S3SERVER = "yes"
        STAGE_03_MINI_PROV = "yes"
        STAGE_04_START_S3SERVER = "yes"
        STAGE_05_VALIDATE_DEPLOYMENT = "yes"
    }

    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        buildDiscarder(logRotator(numToKeepStr: "30"))
    }

    stages {

        // Clone deploymemt scripts from cortx-re repo
        stage ('Prerequisite') {
            steps {
                script {
                    
                    // Add badget to jenkins build
                    manager.addHtmlBadge("&emsp;<b>Build :</b> <a href=\"${CORTX_BUILD}\"><b>${build_id}</b></a> <br /> <b>Host :</b> ${NODE1_HOST}")

                    sh """
                        set +x
                        echo "--------------HW DEPLOYMENT PARAMETERS -------------------"
                        echo "REIMAGE           = ${REIMAGE}"
                        echo "NODE1             = ${NODE1_HOST}"
                        echo "CORTX_BUILD       = ${CORTX_BUILD}"
                        echo "-----------------------------------------------------------"
                    """

                    // Clone cortx-re repo
                    dir('cortx-re') {
                        checkout([$class: 'GitSCM', branches: [[name: '*/EOS-16376']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])                
                    }
                }
            }
        }

        // Prepare deployment environment - (passwordless ssh, installing requireed tools..etc)
        stage('00. Prepare Environment') {
            when { expression { env.STAGE_00_PREPARE_ENV == "yes" } }
            steps {
                script {
                    
                    info("Running '00. Prepare Environment' Stage")  

                    runAnsible("00_PREP_ENV")
                }
            }
        }

        // Re-image vm for cleaner deployment using cloudform api
        stage('00. Re-Image System') {
            when { expression { env.STAGE_00_REIMAGE == "yes"  } }
            steps {
                script {
                    
                    info("Running '00. Re-Image System' Stage")

                     runAnsible("00_REIMAGE")

                }
            }      
        }

        // Execute s3 mini provisioning prereq steps  
        // Ref - https://github.com/Seagate/cortx-s3server/wiki/S3server-provisioning-on-single-node-cluster:-Manual#pre-requisites
        stage('01. Prereq') {
            when { expression { env.STAGE_01_PREREQ == "yes" } }
            steps {
                script {
                    
                    info("Running '01. Prereq' Stage")

                    runAnsible("01_PREREQ")

                }
            } 
        }

        // Install s3server and dependent component(motr,cortx-pyutils) from the provided build
        // Ref - https://github.com/Seagate/cortx-s3server/wiki/S3server-provisioning-on-single-node-cluster:-Manual#install-cortx-s3server-and-cortx-motr-packages
        stage('02. Install S3server') {
            when { expression { env.STAGE_02_INSTALL_S3SERVER == "yes" } }
            steps {
                script {
                    
                    info("Running '02. Deploy Cortx Stack' Stage")

                    runAnsible("02_INSTALL_S3SERVER")

                }
            } 
        }

        // Execute s3 mini provisioning to configure the deployment attributes
        // Ref - https://github.com/Seagate/cortx-s3server/wiki/S3server-provisioning-on-single-node-cluster:-Manual#s3server-mini-provisioning 
        stage('03. Mini Provisioning') {
            when { expression { env.STAGE_03_MINI_PROV == "yes" } }
            steps {
                script {
                    
                    info("Running '03. Mini Provisioning' Stage")

                    runAnsible("03_MINI_PROV")

                }
            } 
        }

        // Start S3Server, Motr to perform I/O
        // Ref - https://github.com/Seagate/cortx-s3server/wiki/S3server-provisioning-on-single-node-cluster:-Manual#start-s3server-and-motr-for-io
        stage('04. Start S3server') {
            when { expression { env.STAGE_04_START_S3SERVER == "yes" } }
            steps {
                script {
                    
                    info("Running '04. Start S3server' Stage")

                    runAnsible("04_START_S3SERVER")

                }
            } 
        }

        // Validate the deployment by performing basic i/o using s3cli command
        stage('05. Validate Deployment') {
            when { expression { env.STAGE_05_VALIDATE_DEPLOYMENT == "yes" } }
            steps {
                script {
                    
                    info("Running '05. Validate Deployment' Stage")

                    runAnsible("05_VALIDATE")

                }
            } 
        }
	}

    post { 
        always {
            script {

                // Download deployment log files from deployment node
                try {
                    sh label: 'download_log_files', returnStdout: true, script: """ 
                        sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/root/*.log .
                    """
                } catch (err) {
                    echo err.getMessage()
                }

                // Cleanup Deployment Node
                build job: 'Cortx-Automation/Deployment/VM-Cleanup', wait: false, parameters: [string(name: 'NODE_LABEL', value: "${env.NODE_NAME}")]
                                
                // Define build status based on hctl command
                hctl_status = ""
                if (fileExists('hctl_status.log')) { 
                    hctl_status = readFile(file: 'hctl_status.log')
                    MESSAGE = "S3Server Deployment Completed"
                    ICON = "accept.gif"
                }else {
                    manager.buildFailure()
                    MESSAGE = "S3Server Deployment Failed"
                    ICON = "error.gif"
                }

                hctl_status_html = "<textarea rows=20 cols=200 readonly style='margin: 0px; height: 392px; width: 843px;'>${hctl_status}</textarea>"
                table_summary = "<table border='1' cellspacing='0' cellpadding='0' width='400' align='left'> <tr> <td align='center'>Build</td><td align='center'><a href=${CORTX_BUILD}>${build_id}</a></td></tr><tr> <td align='center'>Test HW</td><td align='center'>${NODE1_HOST}</td></tr></table>"
                manager.createSummary("${ICON}").appendText("<h3>${MESSAGE} for the build <a href=\"${CORTX_BUILD}\">${build_id}.</a></h3><p>Please check <a href=\"${CORTX_BUILD}/artifact/setup.log\">setup.log</a> for more info <br /><br /><h4>Test Details:</h4> ${table_summary} <br /><br /><br /><h4>HCTL Status:${hctl_status_html}</h4> ", false, false, false, "red")
              
                 // Archive all log generated by Test
                archiveArtifacts artifacts: "**/*.log", onlyIfSuccessful: false, allowEmptyArchive: true 
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


// Used Jenkins ansible plugin to execute ansible command
def runAnsible(tags) {
    withCredentials([usernamePassword(credentialsId: "${NODE_UN_PASS_CRED_ID}", passwordVariable: 'SERVICE_PASS', usernameVariable: 'SERVICE_USER'), usernameColonPassword(credentialsId: "${CLOUDFORM_TOKEN_CRED_ID}", variable: 'CLOUDFORM_API_CRED')]) {
        
        dir("cortx-re/scripts/mini_provisioner") {
            ansiblePlaybook(
                playbook: 's3server_deploy.yml',
                inventory: 'inventories/hosts',
                tags: "${tags}",
                extraVars: [
                    "REIMAGE"               : [value: "${REIMAGE}", hidden: false],
                    "NODE1"                 : [value: "${NODE1_HOST}", hidden: false],
                    "CORTX_BUILD"           : [value: "${CORTX_BUILD}", hidden: false] ,
                    "CLUSTER_PASS"          : [value: "${CLUSTER_PASS}", hidden: false],
                    "CLOUDFORM_API_CRED"    : [value: "${CLOUDFORM_API_CRED}", hidden: true],
                    "SERVICE_USER"          : [value: "${SERVICE_USER}", hidden: true],
                    "SERVICE_PASS"          : [value: "${SERVICE_PASS}", hidden: true],
                    "LDAP_ROOT_USER"        : [value: "${LDAP_ROOT_USER}", hidden: false],
                    "LDAP_ROOT_PWD"         : [value: "${LDAP_ROOT_PWD}", hidden: true],
                    "LDAP_SGIAM_USER"       : [value: "${LDAP_SGIAM_USER}", hidden: false],
                    "LDAP_SGIAM_PWD"        : [value: "${LDAP_SGIAM_PWD}", hidden: true],
                    "BMC_USER"              : [value: "${BMC_USER}", hidden: false],
                    "BMC_SECRET"            : [value: "${BMC_SECRET}", hidden: true]
                ],
                extras: '-v',
                colorized: true
            )
        }
    }
}

// Used below methods for logging
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