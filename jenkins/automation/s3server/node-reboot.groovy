pipeline {
    agent { label 'docker-cp-centos-7.8.2003-node' }

    options {
        timeout(50)
        timestamps()
        ansiColor('xterm') 
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: "30"))
    }

    environment {
        VM_CRED = credentials('node-user')
		JENKINS_API = credentials('jenkins_rest_token')
        GITHUB_TOKEN = credentials('cortx-admin-github')
    }

    parameters {
		string(name: 'REPO_URL', defaultValue: 'https://github.com/shailesh-vaidya/cortx-re', description: 'Repository URL to be used for cortx-re.')
		string(name: 'REPO_BRANCH', defaultValue: 'main', description: 'Branch to be used for cortx-re repo.')
		string(name: 'REBOOT_LABEL', defaultValue: 'reboot-test', description: 'Node Lable for reboot')
	}

    stages {
        
        stage ('Checkout Scripts') {
            steps {
                script {
            
					// Clone cortx-re repo
                    dir('cortx-re') {
                        checkout([$class: 'GitSCM', branches: [[name: "$REPO_BRANCH"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "$REPO_URL"]])                
                    }

                }
            }
        }    

		stage('Reboot System') {
            steps {
                script {

                // Move this code to Ansible playbook       
                    def nodelist = nodesByLabel ("${params.REBOOT_LABEL}")
                    print nodelist
                    env.NODE_LIST=nodelist.join(",")

                }	
                
                // Move this code to Ansible playbook
                sh label: 'Install Ansible', returnStatus: true, script: """

                yum install ansible -y
                  
                for i in \${NODE_LIST//,/ }; do NODE_IP_LIST=\$NODE_IP_LIST,\$(curl -s -X POST -L --user "$JENKINS_API_USR:$JENKINS_API_PSW" -d "script=println InetAddress.localHost.hostAddress" http://eos-jenkins.mero.colo.seagate.com/computer/\$i/scriptText); done
                    
                    pushd cortx-re/scripts/automation/server-reboot/
                        #Take nodes offline
                        ansible-playbook node-reboot.yml --tags jenkins-offline -i \$NODE_LIST  --extra-vars "ansible_ssh_pass=$VM_CRED_PSW jenkins_password="$JENKINS_API_PSW" jenkins_user=$JENKINS_API_USR"
                        
                        #Reboot nodes
                        ansible-playbook node-reboot.yml --tags reboot -i \$NODE_IP_LIST  --extra-vars "ansible_ssh_pass=$VM_CRED_PSW"
                        
                        #Bring nodes online
                        ansible-playbook node-reboot.yml --tags jenkins-online -i \$NODE_LIST  --extra-vars "ansible_ssh_pass=$VM_CRED_PSW jenkins_password="$JENKINS_API_PSW" jenkins_user=$JENKINS_API_USR"
                    popd
                    
                """
            }
		}
	}
}