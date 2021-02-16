#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            label 'docker-io-centos-7.8.2003-node'
        }
    }

    options { 
        skipDefaultCheckout()
        timeout(time: 180, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')  
    }

    parameters {  
	    string(name: 'CSM_URL', defaultValue: 'https://github.com/Seagate/cortx-manager', description: 'Repo for CSM Agent')
        string(name: 'CSM_BRANCH', defaultValue: 'main', description: 'Branch for CSM Agent')     
	}

    environment {

        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        CSM_URL = "${ghprbGhRepository != null ? GPR_REPO : CSM_URL}"
        CSM_BRANCH = "${sha1 != null ? sha1 : CSM_BRANCH}"

        CSM_GPR_REFSEPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        CSM_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        CSM_PR_REFSEPEC = "${ghprbPullId != null ? CSM_GPR_REFSEPEC : CSM_BRANCH_REFSEPEC}"

        //////////////////////////////// BUILD VARS //////////////////////////////////////////////////

        COMPONENT_NAME = "csm-agent".trim()
        BRANCH = "main"
        OS_VERSION = "centos-7.8.2003"

        // 'WARNING' - rm -rf command used on this path please careful when updating this value
        DESTINATION_RELEASE_LOCATION = "/mnt/bigstorage/releases/cortx/github/pr-build/${COMPONENT_NAME}/${BUILD_NUMBER}"
        CORTX_BUILD = "http://cortx-storage.colo.seagate.com/releases/cortx/github/pr-build/${COMPONENT_NAME}/${BUILD_NUMBER}"

        ////////////////////////////////// DEPLOYMENT VARS /////////////////////////////////////////////////////

        // NODE1_HOST - Env variables added in the node configurations
        build_id = sh(script: "echo ${CORTX_BUILD} | rev | cut -d '/' -f2,3 | rev", returnStdout: true).trim()

        // GID/pwd used to update root password 
        NODE_UN_PASS_CRED_ID = "mini-prov-change-pass"

        // Credentials used to for node SSH
        NODE_DEFAULT_SSH_CRED  = credentials("vm-deployment-ssh-cred")
        NODE_USER = "${NODE_DEFAULT_SSH_CRED_USR}"
        NODE_PASS = "${NODE_DEFAULT_SSH_CRED_PSW}"
        CLUSTER_PASS = "${NODE_DEFAULT_SSH_CRED_PSW}"

        STAGE_DEPLOY = "yes"
    }

    stages {

        // Build csm fromm PR source code
        stage('Build') {
            steps {
				script { build_stage = env.STAGE_NAME }
                 
                dir("csm") {

                    checkout([$class: 'GitSCM', branches: [[name: "${CSM_BRANCH}"]], doGenerateSubmoduleConfigurations: false,  extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CSM_URL}",  name: 'origin', refspec: "${CSM_PR_REFSEPEC}"]]])

                    sh label: '', script: '''
                        sed -i 's/gpgcheck=1/gpgcheck=0/' /etc/yum.conf
                        yum-config-manager --add http://cortx-storage.colo.seagate.com/releases/cortx/github/$BRANCH/$OS_VERSION/last_successful/
                        yum-config-manager --add http://cortx-storage.colo.seagate.com/releases/cortx/components/github/$BRANCH/$OS_VERSION/dev/cortx-utils/last_successful/
                        yum clean all && rm -rf /var/cache/yum
                        pip3.6 install  pyinstaller==3.5
                    '''

                    // Exclude return code check for csm_setup and csm_test
                    sh label: 'Build', returnStatus: true, script: '''
                        BUILD=$(git rev-parse --short HEAD)
                        VERSION=$(cat VERSION)
                        echo "Executing build script"
                        echo "VERSION:$VERSION"
                        echo "Python:$(python --version)"
                        ./cicd/build.sh -v $VERSION -b $BUILD_NUMBER -t
                    '''

                    sh label: 'Collect Release Artifacts', script: '''
                    
                        rm -rf "${DESTINATION_RELEASE_LOCATION}"
                        mkdir -p "${DESTINATION_RELEASE_LOCATION}"
            
                        if [[ ( ! -z `ls ./dist/rpmbuild/RPMS/x86_64/*.rpm `)]]; then
                            cp ./dist/rpmbuild/RPMS/x86_64/*.rpm "${DESTINATION_RELEASE_LOCATION}"
                        else
                            echo "RPM not exists !!!"
                            exit 1
                        fi

                        pushd ${DESTINATION_RELEASE_LOCATION}
                            rpm -qi createrepo || yum install -y createrepo
                            createrepo .
                        popd
                    '''	
                }
            }
        }


        // Deploy csm mini provisioner 
        stage('Deploy') {
            agent { node { label "mini_provisioner && !cleanup_req" } }
            when { expression { env.STAGE_DEPLOY == "yes" } }
            steps {
                script { build_stage = env.STAGE_NAME }
                script {

                    // Cleanup Workspace
                    cleanWs()

                    markNodeforCleanup()

                    manager.addHtmlBadge("&emsp;<b>Deployment Host :</b><a href='${JENKINS_URL}/computer/${env.NODE_NAME}'> ${NODE1_HOST}</a>&emsp;")

                    // Run Deployment
                    catchError {
                        
                        dir('cortx-re') {
                            checkout([$class: 'GitSCM', branches: [[name: '*/mini-provisioner-dev']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true], [$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
                        }

                        runAnsible("00_PREP_ENV, 01_DEPLOY")

                    }

                    // Trigger cleanup VM
                    build job: 'Cortx-Automation/Deployment/VM-Cleanup', wait: false, parameters: [string(name: 'NODE_LABEL', value: "${env.NODE_NAME}")]

                    // Cleanup Workspace
                    cleanWs()
                }
            }
        }
	}

    post {
        failure {
            script {       
                //manager.addShortText("${build_stage} Failed")

                sh label: 'Remove artifacts', script: '''
                    rm -rf "${DESTINATION_RELEASE_LOCATION}"
                '''
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
    withCredentials([usernamePassword(credentialsId: "${NODE_UN_PASS_CRED_ID}", passwordVariable: 'SERVICE_PASS', usernameVariable: 'SERVICE_USER'), string(credentialsId: "${CLOUDFORM_TOKEN_CRED_ID}", variable: 'CLOUDFORM_API_CRED')]) {
        
        dir("cortx-re/scripts/mini_provisioner") {
            ansiblePlaybook(
                playbook: 'csm_deploy.yml',
                inventory: 'inventories/hosts',
                tags: "${tags}",
                extraVars: [
                    "NODE1"                 : [value: "${NODE1_HOST}", hidden: false],
                    "CORTX_BUILD"           : [value: "${CORTX_BUILD}", hidden: false] ,
                    "CLUSTER_PASS"          : [value: "${CLUSTER_PASS}", hidden: false],
                    "CLOUDFORM_API_CRED"    : [value: "${CLOUDFORM_API_CRED}", hidden: true],
                    "SERVICE_USER"          : [value: "${SERVICE_USER}", hidden: true],
                    "SERVICE_PASS"          : [value: "${SERVICE_PASS}", hidden: true],
                ],
                extras: '-v',
                colorized: true
            )
        }
    }
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