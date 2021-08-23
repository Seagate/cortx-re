#!/usr/bin/env groovy
pipeline { 
        agent {
            node {
                // Run deployment on mini_provisioner nodes (vm deployment nodes)
                label params.HOST == "-" ? "mini_provisioner_s3_7_9 && !cleanup_req" : "mini_provisioner_s3_user_host"
                customWorkspace "/var/jenkins/mini_provisioner/${JOB_NAME}_${BUILD_NUMBER}"
            }
        }
        parameters {  
	    string(name: 'CORTX_BUILD', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/cortx/github/main/centos-7.9.2009/last_successful_prod/', description: 'Build URL')
        choice(name: 'MINI_PROV_END_STEP', choices: ["Cleanup", "Reset", "Test", "Init", "Config", "Prepare", "Post_Install", "Pre_Requisites" ], description: '''<pre>

Cleanup         -> <a href="https://github.com/Seagate/cortx-utils/wiki/OpenLDAP-Setup#cleanup">openldap-provisioning-on-single-node-VM-cluster:-Manual#openldapcleanup</a>
Reset           -> <a href="https://github.com/Seagate/cortx-utils/wiki/OpenLDAP-Setup#reset">openldap-provisioning-on-single-node-VM-cluster:-Manual#openldapreset</a>
Test            -> <a href="https://github.com/Seagate/cortx-utils/wiki/OpenLDAP-Setup#test">openldap-provisioning-on-single-node-VM-cluster:-Manual#openldaptest</a>
Init            -> <a href="https://github.com/Seagate/cortx-utils/wiki/OpenLDAP-Setup#init">openldap-provisioning-on-single-node-VM-cluster:-Manual#openldapinit</a>
Config          -> <a href="https://github.com/Seagate/cortx-utils/wiki/OpenLDAP-Setup#config">openldap-provisioning-on-single-node-VM-cluster:-Manual#openldapconfig</a>
Prepare         -> <a href="https://github.com/Seagate/cortx-utils/wiki/OpenLDAP-Setup#prepare">openldap-provisioning-on-single-node-VM-cluster:-Manual#openldapprepare</a>
Post_Install    -> <a href="https://github.com/Seagate/cortx-utils/wiki/OpenLDAP-Setup#post-install">openldap-provisioning-on-single-node-VM-cluster:-Manual#openldappost_install</a>
Pre_Requisites  -> <a href="https://github.com/Seagate/cortx-utils/wiki/OpenLDAP-Setup#install-cortx-utils-rpm">openldap-provisioning-on-single-node-VM-cluster:-Manual#pre-requisites</a>

</pre>''')
        choice(name: 'DEBUG', choices: ["no", "yes" ], description: '''<pre>
NOTE : Only applicable when 'HOST' parameter is provided

no -> Cleanup the vm on post deployment  
yes -> Preserve host for troublshooting [ WARNING ! Automated Deployment May be queued/blocked if more number of vm used for debuging ]  
</pre>''')
        string(name: 'HOST', defaultValue: '-', description: '''<pre>
FQDN of ssc-vm

Recommended VM specification:
- Cloudform VM Template : LDRr2 - CentOS 7.9  
- vCPUs                 : 4  
- Memory (RAM)          : 8GB  
- Additional Disks      : 2   
- Additional Disk Size  : 25 GB  
</pre>
        ''',  trim: true)
        password(name: 'HOST_PASS', defaultValue: '-', description: 'VM <b>root</b> user password')
        }
        environment {
            // Credentials used to SSH node
            NODE_DEFAULT_SSH_CRED =  credentials("${NODE_DEFAULT_SSH_CRED}")
            NODE_USER = "${NODE_DEFAULT_SSH_CRED_USR}"
            NODE1_HOST = "${params.HOST == '-' ? NODE1_HOST : params.HOST }"
            NODE_PASS = "${HOST_PASS == '-' ? NODE_DEFAULT_SSH_CRED_PSW : HOST_PASS}"
        }
        stages {
            stage ('Prerequisite') {
                steps {
                    script { build_stage = env.STAGE_NAME }
                    script {
                        
                        // Add badget to jenkins build
                        manager.addHtmlBadge("&emsp;<b>Build :</b> <a href=\"${CORTX_BUILD}\"><b>${build_id}</b></a> <br /> &emsp;<b>Deployment Host :</b><a href='${JENKINS_URL}/computer/${env.NODE_NAME}'> ${NODE1_HOST}</a>&emsp;")

                        sh """
                            set +x
                            echo "--------------HW DEPLOYMENT PARAMETERS -------------------"
                            echo "NODE1             = ${NODE1_HOST}"
                            echo "CORTX_BUILD       = ${CORTX_BUILD}"
                            echo "-----------------------------------------------------------"
                        """

                        // Clone cortx-re repo
                        dir('cortx-re') {
                            checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])                
                        }
                        
                        if ( "${params.HOST}" == "-" ) {
                            markNodeforCleanup()
                        }
                    }
                }
            }

            stage('00. Prepare Environment') {
                steps {
                    script { build_stage = env.STAGE_NAME }
                    script {
                        
                        runAnsible("00_PREP_ENV")
                    }
                }
            }

            stage('01. Pre_Requisites') {
                when { expression { params.MINI_PROV_END_STEP ==~ /Pre_Requisites|Post_Install|Prepare|Config|Init|Start|Test|Reset|Cleanup/ } }
                steps {
                    script { build_stage = env.STAGE_NAME }
                    script {
                        
                        runAnsible("01_PREREQ")

                    }
                } 
            }

            stage('02.1 : Post_Install') {
                when { expression { params.MINI_PROV_END_STEP ==~ /Post_Install|Prepare|Config|Init|Start|Test|Reset|Cleanup/ } }
                steps {
                    script { build_stage = env.STAGE_NAME }
                    script {
                        
                        runAnsible("02_MINI_PROV_POST_INSTALL")
                    }
                } 
            }

            stage('02.2 : Prepare') {
                when { expression { params.MINI_PROV_END_STEP ==~ /Prepare|Config|Init|Start|Test|Reset|Cleanup/ } }
                steps {
                    script { build_stage = env.STAGE_NAME }
                    script {
                        
                        runAnsible("02_MINI_PROV_PREPARE")

                    }
                } 
            }

            stage('02.3 : Config') {
                when { expression { params.MINI_PROV_END_STEP ==~ /Config|Init|Start|Test|Reset|Cleanup/ } }
                steps {
                    script { build_stage = env.STAGE_NAME }
                    script {
                        
                        runAnsible("02_MINI_PROV_CONFIG")

                    }
                } 
            }

            stage('02.4 : Init') {
                when { expression { params.MINI_PROV_END_STEP ==~ /Init|Start|Test|Reset|Cleanup/ } }
                steps {
                    script { build_stage = env.STAGE_NAME }
                    script {
                        
                        runAnsible("02_MINI_PROV_INIT")

                    }
                } 
            }

            stage('03 : Test') {
                when { expression { params.MINI_PROV_END_STEP ==~ /Test|Reset|Cleanup/ } }
                steps {
                    script { build_stage = env.STAGE_NAME }
                    script {

                        runAnsible("03_VALIDATE")

                    }
                } 
            }
        }
        post { 
            always {
                script {

                    runAnsible("SUPPORT_BUNDLE")

                    // Download deployment log files from deployment node
                    try {
                        sh label: 'download_log_files', returnStdout: true, script: """ 
                            mkdir -p artifacts
                            sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/root/*.log artifacts/ || true
                            sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/root/support_bundle artifacts/ || true
                            sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/etc/haproxy/haproxy.cfg artifacts/ || true
                            sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/opt/seagate/cortx/s3/conf/*1-node artifacts/ || true
                            sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/opt/seagate/cortx/s3/s3backgrounddelete/config.yaml artifacts/s3backgrounddelete_config.yaml || true
                            sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/tmp/cortx-config-new artifacts/ || true
                            sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/etc/hosts artifacts/ || true
                            sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/opt/seagate/cortx/s3/mini-prov/*.json artifacts/ || true
                        """
                    } catch (err) {
                        echo err.getMessage()
                    }

                    archiveArtifacts artifacts: "artifacts/**/*.*", onlyIfSuccessful: false, allowEmptyArchive: true                           
                }
            }
            cleanup {
                script {

                    if ( params.MINI_PROV_END_STEP == "Reset" ||  params.MINI_PROV_END_STEP == "Cleanup") {
                        runAnsible("05_MINI_PROV_RESET")
                    } 

                    if ( params.MINI_PROV_END_STEP == "Cleanup" ) {
                        runAnsible("05_MINI_PROV_CLEANUP")
                    } 
                    
                    if ( "${params.HOST}" == "-" ) {

                        if ( "${params.DEBUG}" == "yes" ) {  
                            markNodeOffline("S3 Debug Mode Enabled on This Host  - ${BUILD_URL}")
                        } else {
                            build job: 'Cortx-Automation/Deployment/VM-Cleanup', wait: false, parameters: [string(name: 'NODE_LABEL', value: "${env.NODE_NAME}")]                    
                        }
                    } 

                    // Archive all log generated by Test
                    cleanWs()
                } 
            }
        }
	}

    // post {
    //     always {
    //         script  {
                
    //             sh label: 'Delete Old Builds', script: '''
    //                 set +x
    //                 find /mnt/bigstorage/releases/cortx/github/pr-build/${COMPONENT_NAME}/* -maxdepth 0 -mtime +30 -type d -exec rm -rf {} \\;
	// 			'''

    //             if (env.ghprbPullLink) {
    //                 env.pr_id = "${ghprbPullLink}"
    //             } else {
    //                 env.branch_name = "${S3_BRANCH}"
    //                 env.repo_url = "${S3_URL}"
    //             }
    //             env.build_stage = "${build_stage}"
                
    //             def mailRecipients = "nilesh.govande@seagate.com, basavaraj.kirunge@seagate.com, rajesh.nambiar@seagate.com, amit.kumar@seagate.com"
    //             emailext body: '''${SCRIPT, template="mini_prov-email.template"}''',
    //             mimeType: 'text/html',
    //             recipientProviders: [requestor()], 
    //             subject: "[Jenkins] S3AutoMiniProvisioning : ${currentBuild.currentResult}, ${JOB_BASE_NAME}#${BUILD_NUMBER}",
    //             to: "${mailRecipients}"
    //         }
    //     }
    //     failure {
    //         script {
    //             manager.addShortText("${build_stage} Failed")
    //         }  
    //     }
    // }
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
      
    dir("cortx-re/scripts/mini_provisioner") {

        ansiblePlaybook(
            playbook: 'openldap.yml',
            inventory: 'inventories/hosts',
            tags: "${tags}",
            extraVars: [
                "NODE1"                 : [value: "${NODE1_HOST}", hidden: false],
                "CORTX_BUILD"           : [value: "${CORTX_BUILD}", hidden: false] ,
                "CLUSTER_PASS"          : [value: "${NODE_PASS}", hidden: false]            
            ],
            extras: '-v',
            colorized: true
        )
    }
}

// Create Summary
def addSummary() {

    hctl_status = ""
    if (fileExists ('artifacts/hctl_status.log')) {
        hctl_status = readFile(file: 'artifacts/hctl_status.log')
        MESSAGE = "S3Server Deployment Completed"
        ICON = "accept.gif"
    } else {
        manager.buildFailure()
        MESSAGE = "S3Server Deployment Failed"
        ICON = "error.gif"
    }

    hctl_status_html = "<textarea rows=20 cols=200 readonly style='margin: 0px; height: 392px; width: 843px;'>${hctl_status}</textarea>"
    table_summary = "<table border='1' cellspacing='0' cellpadding='0' width='400' align='left'> <tr> <td align='center'>Build</td><td align='center'><a href=${CORTX_BUILD}>${build_id}</a></td></tr><tr> <td align='center'>Test VM</td><td align='center'>${NODE1_HOST}</td></tr></table>"
    manager.createSummary("${ICON}").appendText("<h3>${MESSAGE} for the build <a href=\"${CORTX_BUILD}\">${build_id}.</a></h3><br /><br /><h4>Test Details:</h4> ${table_summary} <br /><br /><br /><h4>HCTL Status:${hctl_status_html}</h4> ", false, false, false, "red")
              
}


// Mark node for cleanup ( cleanup job will use this node label to identify cleanup node)
def markNodeforCleanup() {
	nodeLabel = "cleanup_req"
    node = getCurrentNode(env.NODE_NAME)
	node.setLabelString(node.getLabelString()+" "+nodeLabel)
	node.save()
    node = null
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

def getCurrentNode(nodeName) {
  for (node in Jenkins.instance.nodes) {
      if (node.getNodeName() == nodeName) {
        echo "Found node for $nodeName"
        return node
    }
  }
  throw new Exception("No node for $nodeName")
}