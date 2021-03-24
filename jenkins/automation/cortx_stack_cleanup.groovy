#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            // This job runs on vm deployment controller node to execute vm cleanup for the deployment configured host
            label "${NODE_LABEL} && cleanup_req"
        }
    }
	
    // Accept node label as parameter
    parameters {
        string(name: 'NODE_LABEL', defaultValue: 'cleanup_req', description: 'Node Label',  trim: true)
    }

	environment {
        // NODE1_HOST - Env variables added in the node configurations

        // Credentials used to SSH node
        NODE_DEFAULT_SSH_CRED = credentials("${NODE_DEFAULT_SSH_CRED}")
        NODE_USER = "${NODE_DEFAULT_SSH_CRED_USR}"
        NODE_PASS = "${NODE_DEFAULT_SSH_CRED_PSW}"
    }

    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        buildDiscarder(logRotator(numToKeepStr: "30"))
    }

    
    stages {
        
        stage("SSH Connectivity Check") {
            steps {
                script {
                    def remoteHost = getTestMachine("${NODE1_HOST}", "${NODE_USER}", "${NODE_PASS}")
                    sshCommand remote: remoteHost, command: "exit"
                    echo "Successfully connected to VM ${env.NODE_NAME}!"
                }
            }    
        }
        stage("Teardown of Cortx Stack!") {
            steps {
                script {
                    def remoteHost = getTestMachine("${NODE1_HOST}", "${NODE_USER}", "${NODE_PASS}")
                    sshCommand remote: remoteHost, command: """
                        /opt/seagate/cortx/provisioner/cli/destroy-vm --ctrlpath-states --iopath-states --prereq-states --system-states --ha-states || true
                    """
                    echo "Successfully teardown of Cortx Stack!"
                }
            }
        }
        stage("Stop Salt services") {
            steps {
                script {
                    def remoteHost = getTestMachine("${NODE1_HOST}", "${NODE_USER}", "${NODE_PASS}")
                    sshCommand remote: remoteHost, command: """
                        systemctl status glustersharedstorage >/dev/null && systemctl stop glustersharedstorage || true
                        systemctl status glusterfsd >/dev/null && systemctl stop glusterfsd || true
                        systemctl status glusterd >/dev/null && systemctl stop glusterd || true
                        systemctl status salt-minion >/dev/null && systemctl stop salt-minion || true
                        systemctl status salt-master >/dev/null && systemctl stop salt-master || true
                    """
                    echo "Sucessfully stopped salt and gluster services"
                }
            }
        }
        stage("Uninstall rpms") {
            steps {
                script {
                    def remoteHost = getTestMachine("${NODE1_HOST}", "${NODE_USER}", "${NODE_PASS}")
                    sshCommand remote: remoteHost, command: """
                        yum erase -y cortx-prvsnr cortx-prvsnr-cli      # Cortx Provisioner packages
                        yum erase -y gluster-fuse gluster-server        # Gluster FS packages
                        yum erase -y salt-minion salt-master salt-api   # Salt packages
                        yum erase -y python36-m2crypto                  # Salt dependency
                        yum erase -y python36-cortx-prvsnr              # Cortx Provisioner API packages
                        # Brute clean for any cortx rpm packages
                        yum erase -y *cortx*
                        # Re-condition yum db
                        yum autoremove -y
                        yum clean all
                        rm -rf /var/cache/yum
                        # Remove cortx-py-utils
                        pip3 uninstall -y cortx-py-utils
                        # Cleanup pip packages
                        pip3 freeze|xargs pip3 uninstall -y
                        # Cleanup pip config
                        test -e /etc/pip.conf && rm -f /etc/pip.conf
                        rm -rf ~/.cache/pip
                    """
                    echo "Successfully uninstalled rpms"
                }
            }
        }
        stage("Cleanup bricks and other directories") {
            steps {
                script {
                    def remoteHost = getTestMachine("${NODE1_HOST}", "${NODE_USER}", "${NODE_PASS}")
                    sshCommand remote: remoteHost, command: """
                        # Cortx software dirs
                        rm -rf /opt/seagate/cortx
                        rm -rf /opt/seagate/cortx_configs
                        rm -rf /opt/seagate
                        # Bricks cleanup
                        test -e /var/lib/seagate && rm -rf /var/lib/seagate || true
                        test -e /srv/glusterfs && rm -rf /srv/glusterfs || true
                        # Cleanup Salt
                        test -e /var/cache/salt && rm -rf /var/cache/salt || true
                        test -e /etc/salt && rm -rf /etc/salt || true
                        # Cleanup Provisioner profile directory
                        test -e /opt/isos && rm -rf /opt/isos || true
                        test -e /root/.provisioner && rm -rf /root/.provisioner || true
                        test -e /etc/yum.repos.d/RELEASE_FACTORY.INFO && rm -f /etc/yum.repos.d/RELEASE_FACTORY.INFO || true
                        test -e /root/.ssh && rm -rf /root/.ssh || true
                    """
                    echo "Successfully removed!"
                }
            }
        }
    }

    post {
        failure {
            script {
                // On cleanup failure take node offline
                markNodeOffline(" VM Re-Image Issue  - Automated offline")
            }
        }
        success {
            script {
                // remove cleanup label from the node
                removeCleanupLabel()
            }
        }
    }
}	


// Method returns VM Host Information ( host, ssh cred )
def getTestMachine(host, user, pass) {

    def remote = [:]
    remote.name = 'cortx'
    remote.host = host
    remote.user =  user
    remote.password = pass
    remote.allowAnyHosts = true
    return remote
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

def removeCleanupLabel() {
	nodeLabel = "cleanup_req"
    node = getCurrentNode(env.NODE_NAME)
	node.setLabelString(node.getLabelString().replaceAll(nodeLabel, ""))
    echo "[ ${env.NODE_NAME} ] : Cleanup label removed. The current node labels are ( ${node.getLabelString()} )"
	node.save()
    node = null
    
}

// Get running node instance
def getCurrentNode(nodeName) {
  for (node in Jenkins.instance.nodes) {
      if (node.getNodeName() == nodeName) {
        echo "Found node for $nodeName"
        return node
    }
  }
  throw new Exception("No node for $nodeName")
}
