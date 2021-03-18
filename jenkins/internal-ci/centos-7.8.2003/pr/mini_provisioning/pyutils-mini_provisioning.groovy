#!/usr/bin/env groovy
// CLEANUP REQUIRED
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
	    string(name: 'PY_UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'Repo for Py-Utils Agent')
        string(name: 'PY_UTILS_BRANCH', defaultValue: 'main', description: 'Branch for Py-Utils Agent')
        choice(name: 'DEBUG', choices: ["no", "yes" ], description: 'Keep Host for Debuging')     
        string(name: 'HOST', defaultValue: '-', description: 'Host FQDN',  trim: true)
        password(name: 'HOST_PASS', defaultValue: '-', description: 'Host machine root user password')
	}

    environment {

        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        PY_UTILS_URL = "${ghprbGhRepository != null ? GPR_REPO : PY_UTILS_URL}"
        PY_UTILS_BRANCH = "${sha1 != null ? sha1 : PY_UTILS_BRANCH}"

        PY_UTILS_REFSPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        PY_UTILS_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        PY_UTILS_PR_REFSPEC = "${ghprbPullId != null ? PY_UTILS_REFSPEC : PY_UTILS_BRANCH_REFSEPEC}"

        //////////////////////////////// BUILD VARS //////////////////////////////////////////////////

        COMPONENT_NAME = "cortx-utils".trim()
        BRANCH = "main"
        OS_VERSION = "centos-7.8.2003"
        version = "2.0.0" 

        // 'WARNING' - rm -rf command used on this path please careful when updating this value
        DESTINATION_RELEASE_LOCATION = "/mnt/bigstorage/releases/cortx/github/pr-build/${COMPONENT_NAME}/${BUILD_NUMBER}"
        CORTX_BUILD = "http://cortx-storage.colo.seagate.com/releases/cortx/github/pr-build/${COMPONENT_NAME}/${BUILD_NUMBER}"

        ////////////////////////////////// DEPLOYMENT VARS /////////////////////////////////////////////////////

        // NODE1_HOST - Env variables added in the node configurations
        build_id = sh(script: "echo ${CORTX_BUILD} | rev | cut -d '/' -f2,3 | rev", returnStdout: true).trim()

        STAGE_DEPLOY = "yes"
    }

    stages {

        // Build py_utils fromm PR source code
        stage('Build') {
            steps {
				script { build_stage = env.STAGE_NAME }
                 
                dir("cortx_utils") {

                    checkout([$class: 'GitSCM', branches: [[name: "${PY_UTILS_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${PY_UTILS_URL}",  name: 'origin', refspec: "${PY_UTILS_PR_REFSPEC}"]]])

                    sh label: 'Build', script: '''
                        yum install python36-devel -y
                        pushd py-utils
                            python3.6 setup.py bdist_rpm --version=$version --post-install utils-post-install --post-uninstall utils-post-uninstall --post-uninstall utils-post-uninstall --release="${BUILD_NUMBER}_$(git rev-parse --short HEAD)"
                        popd
                        
                        ./statsd-utils/jenkins/build.sh -v $version -b $BUILD_NUMBER
                    '''

                    sh label: 'Copy RPMS', script: '''

                        rm -rf "${DESTINATION_RELEASE_LOCATION}"
                        mkdir -p "${DESTINATION_RELEASE_LOCATION}"

                        shopt -s extglob
                        cp ./py-utils/dist/!(*.src.rpm|*.tar.gz) "${DESTINATION_RELEASE_LOCATION}"
                        cp ./statsd-utils/dist/rpmbuild/RPMS/x86_64/*.rpm "${DESTINATION_RELEASE_LOCATION}"

                        pushd ${DESTINATION_RELEASE_LOCATION}
                            rpm -qi createrepo || yum install -y createrepo
                            createrepo .
                        popd
                    '''
                }
            }
        }


        // Deploy py_utils mini provisioner 
        stage('Deploy') {
            when { expression { env.STAGE_DEPLOY == "yes" } }
            agent {
                node {
                    // Run deployment on mini_provisioner nodes (vm deployment nodes)
                    label params.HOST == "-" ? "mini_provisioner && !cleanup_req" : "mini_provisioner_user_host"
                    customWorkspace "/var/jenkins/mini_provisioner/${JOB_NAME}_${BUILD_NUMBER}"
                }
            }
            environment {
                // Credentials used to SSH node
                NODE_DEFAULT_SSH_CRED =  credentials("${NODE_DEFAULT_SSH_CRED}")
                NODE_USER = "${NODE_DEFAULT_SSH_CRED_USR}"
                NODE1_HOST = "${HOST == '-' ? NODE1_HOST : HOST }"
                NODE_PASS = "${HOST_PASS == '-' ? NODE_DEFAULT_SSH_CRED_PSW : HOST_PASS}"
            }
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
                    if( "${HOST}" == "-" ) {
                        if( "${DEBUG}" == "yes" ) {  
                            markNodeOffline("Debug Mode Enabled on This Host  - ${BUILD_URL}")
                        } else {
                            build job: 'Cortx-Automation/Deployment/VM-Cleanup', wait: false, parameters: [string(name: 'NODE_LABEL', value: "${env.NODE_NAME}")]                    
                        }
                    }
                    
                    // Cleanup Workspace
                    cleanWs()
                }
            }
        }
	}

    post {
        always {
            sh label: 'Remove artifacts', script: '''rm -rf "${DESTINATION_RELEASE_LOCATION}"'''
        }
        failure {
            script {
                manager.addShortText("${build_stage} Failed")
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
    
    dir("cortx-re/scripts/mini_provisioner") {
        ansiblePlaybook(
            playbook: 'pyutils_deploy.yml',
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