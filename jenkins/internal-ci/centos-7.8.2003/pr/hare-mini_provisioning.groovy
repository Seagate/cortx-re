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
	    string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Repo for Hare')
        string(name: 'HARE_BRANCH', defaultValue: 'main', description: 'Branch for Hare')
        choice(name: 'DEBUG', choices: ["no", "yes" ], description: 'Keep Host for Debuging')   
	}

    environment {

        // Hare Repo Info

        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        HARE_URL = "${ghprbGhRepository != null ? GPR_REPO : HARE_URL}"
        HARE_BRANCH = "${sha1 != null ? sha1 : HARE_BRANCH}"

        HARE_GPR_REFSPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        HARE_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        HARE_PR_REFSPEC = "${ghprbPullId != null ? HARE_GPR_REFSPEC : HARE_BRANCH_REFSEPEC}"

        //////////////////////////////// BUILD VARS //////////////////////////////////////////////////

        COMPONENT_NAME = "hare".trim()
        BRANCH = "main"
        OS_VERSION = "centos-7.8.2003"
        THIRD_PARTY_VERSION = "centos-7.8.2003-2.0.0-latest"
        VERSION = "2.0.0"
        PASSPHARASE = credentials('rpm-sign-passphrase')

        // Artifacts root location

        // 'WARNING' - rm -rf command used on this path please careful when updating this value
        DESTINATION_RELEASE_LOCATION = "/mnt/bigstorage/releases/cortx/github/pr-build/${COMPONENT_NAME}/${BUILD_NUMBER}"
        PYTHON_DEPS = "/mnt/bigstorage/releases/cortx/third-party-deps/python-deps/python-packages-2.0.0-latest"
        THIRD_PARTY_DEPS = "/mnt/bigstorage/releases/cortx/third-party-deps/centos/${THIRD_PARTY_VERSION}/"
        COMPONENTS_RPM = "/mnt/bigstorage/releases/cortx/components/github/${BRANCH}/${OS_VERSION}/dev/"
        CORTX_BUILD = "http://cortx-storage.colo.seagate.com/releases/cortx/github/pr-build/${COMPONENT_NAME}/${BUILD_NUMBER}"

        // Artifacts location
        CORTX_ISO_LOCATION = "${DESTINATION_RELEASE_LOCATION}/cortx_iso"
        THIRD_PARTY_LOCATION = "${DESTINATION_RELEASE_LOCATION}/3rd_party"
        PYTHON_LIB_LOCATION = "${DESTINATION_RELEASE_LOCATION}/python_deps"

        ////////////////////////////////// DEPLOYMENT VARS /////////////////////////////////////////////////////

        // NODE1_HOST - Env variables added in the node configurations

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

        // Build hare fromm PR source code
        stage('Build') {
            steps {
				script { build_stage = env.STAGE_NAME }
                 
                dir("hare") {

                    checkout([$class: 'GitSCM', branches: [[name: "${HARE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false, timeout: 15], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false, timeout: 15]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${HARE_URL}",  name: 'origin', refspec: "${HARE_PR_REFSPEC}"]]])

                    sh label: 'prepare build env', script: """
                        sed '/baseurl/d' /etc/yum.repos.d/motr_current_build.repo
                        echo "baseurl=http://cortx-storage.colo.seagate.com/releases/cortx/components/github/${BRANCH}/${OS_VERSION}/dev/motr/current_build/"  >> /etc/yum.repos.d/motr_current_build.repo
                        yum clean all;rm -rf /var/cache/yum
                        yum install cortx-motr{,-devel} -y
                    """

                    sh label: 'Build', returnStatus: true, script: '''
                        set -xe
                        echo "Executing build script"
                        export build_number=${BUILD_NUMBER}
                        make VERSION=$VERSION rpm
                    '''	
                }
            }
        }

        // Release cortx deployment stack
        stage('Release') {
            steps {
				script { build_stage = env.STAGE_NAME }

                dir('cortx-re') {
                    checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true], [$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
                }

                // Install tools required for release process
                sh label: 'Installed Dependecies', script: '''
                    yum install -y expect rpm-sign rng-tools genisoimage python3-pip
                    pip3 install githubrelease
                    systemctl start rngd
                '''

                // Integrate components rpms
                sh label: 'Collect Release Artifacts', script: '''
                    
                    rm -rf "${DESTINATION_RELEASE_LOCATION}"
                    mkdir -p "${DESTINATION_RELEASE_LOCATION}"

                    if [[ ( ! -z `ls /root/rpmbuild/RPMS/x86_64/*.rpm `)]]; then
                        mkdir -p "${CORTX_ISO_LOCATION}"
                        cp /root/rpmbuild/RPMS/x86_64/*.rpm "${CORTX_ISO_LOCATION}"
                    else
                        echo "RPM not exists !!!"
                        exit 1
                    fi 

                    pushd ${COMPONENTS_RPM}
                        for component in `ls -1 | grep -E -v "${COMPONENT_NAME}"`
                        do
                            echo -e "Copying RPM's for $component"
                            if ls $component/last_successful/*.rpm 1> /dev/null 2>&1; then
                                cp $component/last_successful/*.rpm "${CORTX_ISO_LOCATION}"
                            fi
                        done
                    popd

                    # Symlink 3rdparty repo artifacts
                    ln -s "${THIRD_PARTY_DEPS}" "${THIRD_PARTY_LOCATION}"
                        
                    # Symlink python dependencies
                    ln -s "${PYTHON_DEPS}" "${PYTHON_LIB_LOCATION}"
                '''

                sh label: 'RPM Signing', script: '''
                    pushd cortx-re/scripts/rpm-signing
                        cat gpgoptions >>  ~/.rpmmacros
                        sed -i 's/passphrase/'${PASSPHARASE}'/g' genkey-batch
                        gpg --batch --gen-key genkey-batch
                        gpg --export -a 'Seagate'  > RPM-GPG-KEY-Seagate
                        rpm --import RPM-GPG-KEY-Seagate
                    popd

                    pushd cortx-re/scripts/rpm-signing
                        chmod +x rpm-sign.sh
                        cp RPM-GPG-KEY-Seagate ${CORTX_ISO_LOCATION}
                        for rpm in `ls -1 ${CORTX_ISO_LOCATION}/*.rpm`
                        do
                            ./rpm-sign.sh ${PASSPHARASE} ${rpm}
                        done
                    popd

                '''
                
                sh label: 'RPM Signing', script: '''
                    pushd ${CORTX_ISO_LOCATION}
                        rpm -qi createrepo || yum install -y createrepo
                        createrepo .
                    popd
                '''	

                sh label: 'RPM Signing', script: '''
                    pushd cortx-re/scripts/release_support
                        sh build_release_info.sh -v ${VERSION} -b "${CORTX_ISO_LOCATION}"
                        sh build-3rdParty-release-info.sh "${THIRD_PARTY_LOCATION}"
                        sh build_readme.sh "${DESTINATION_RELEASE_LOCATION}"
                    popd

                    cp "${THIRD_PARTY_LOCATION}/THIRD_PARTY_RELEASE.INFO" "${DESTINATION_RELEASE_LOCATION}"
                    cp "${CORTX_ISO_LOCATION}/RELEASE.INFO" "${DESTINATION_RELEASE_LOCATION}"
                '''		
            }

        }

        // Ref - https://github.com/Seagate/cortx-hare/wiki/Hare-provisioning
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

                        runAnsible("00_PREP_ENV, 01_PREREQ, 02_MINI_PROV")

                    }

                    // Collect logs from test node
                    catchError {

                        sh label: 'download_log_files', returnStdout: true, script: """ 
                            sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/root/*.json .
                        """
                        
                        archiveArtifacts artifacts: "**/*.json", onlyIfSuccessful: false, allowEmptyArchive: true 
                    }

                    if("${DEBUG}" == "yes"){
                        
                        markNodeOffline("Hare Debug Mode Enabled on This Host  - ${JOB_URL}")

                    } else {

                        // Trigger cleanup VM
                        build job: 'Cortx-Automation/Deployment/VM-Cleanup', wait: false, parameters: [string(name: 'NODE_LABEL', value: "${env.NODE_NAME}")]
                   }

                    // Cleanup Workspace
                    cleanWs()
                }
            }
        }
	}

    post {
        failure {
            script {       
                manager.addShortText("${build_stage} Failed")

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
    withCredentials([usernamePassword(credentialsId: "${NODE_UN_PASS_CRED_ID}", passwordVariable: 'SERVICE_PASS', usernameVariable: 'SERVICE_USER'), usernameColonPassword(credentialsId: "${CLOUDFORM_TOKEN_CRED_ID}", variable: 'CLOUDFORM_API_CRED')]) {
        
        dir("cortx-re/scripts/mini_provisioner") {
            ansiblePlaybook(
                playbook: 'hare_deploy.yml',
                inventory: 'inventories/hosts',
                tags: "${tags}",
                extraVars: [
                    "NODE1"                 : [value: "${NODE1_HOST}", hidden: false],
                    "CORTX_BUILD"           : [value: "${CORTX_BUILD}", hidden: false] ,
                    "CLUSTER_PASS"          : [value: "${CLUSTER_PASS}", hidden: false],
                    "CLOUDFORM_API_CRED"    : [value: "${CLOUDFORM_API_CRED}", hidden: true],
                    "SERVICE_USER"          : [value: "${SERVICE_USER}", hidden: true],
                    "SERVICE_PASS"          : [value: "${SERVICE_PASS}", hidden: true]
                ],
                extras: '-v',
                colorized: true
            )
        }
    }
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