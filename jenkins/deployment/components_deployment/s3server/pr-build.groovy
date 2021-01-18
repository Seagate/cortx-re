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

    environment {

        //////////////////////////////// BUILD VARS //////////////////////////////////////////////////

        REPO_URL = "https://github.com/gowthamchinna/cortx-s3server.git"
        COMPONENT_NAME = "s3server".trim()
        BRANCH = "stable"
        OS_VERSION = "centos-7.8.2003"
        THIRD_PARTY_VERSION = "1.0.0-3"
        VERSION = "2.0.0"
        PASSPHARASE = credentials('rpm-sign-passphrase')

        // Get Pull Request Information from ghpr env 
        PULL_ID = "${ghprbPullLink}".substring("${ghprbPullLink}".lastIndexOf('/') + 1).trim()
        PR_IDENTIFIER = "PR-${PULL_ID != null ? PULL_ID : 'TEMP'}"

        // Artifacts root location
        DESTINATION_RELEASE_LOCATION="/mnt/bigstorage/releases/cortx/github/pr-build/${COMPONENT_NAME}/${PR_IDENTIFIER}"
        PYTHON_DEPS="/mnt/bigstorage/releases/cortx/third-party-deps/python-packages"
        THIRD_PARTY_DEPS="/mnt/bigstorage/releases/cortx/third-party-deps/centos/centos-7.8.2003-${THIRD_PARTY_VERSION}/"
        COMPONENTS_RPM="/mnt/bigstorage/releases/cortx/components/github/${BRANCH}/${OS_VERSION}/dev/"
        CORTX_BUILD="http://cortx-storage.colo.seagate.com/releases/cortx/github/pr-build/${COMPONENT_NAME}/${PR_IDENTIFIER}"

        // Artifacts location
        CORTX_ISO_LOCATION="${DESTINATION_RELEASE_LOCATION}/cortx_iso"
        THIRD_PARTY_LOCATION="${DESTINATION_RELEASE_LOCATION}/3rd_party"
        PYTHON_LIB_LOCATION="${DESTINATION_RELEASE_LOCATION}/python_deps"

        ////////////////////////////////// DEPLOYMENT VARS /////////////////////////////////////////////////////

        // NODE1_HOST - Env variables added in the node configurations
        build_id = sh(script: "echo ${CORTX_BUILD} | rev | cut -d '/' -f2,3 | rev", returnStdout: true).trim()

        // GID/pwd used to update root password 
        NODE_UN_PASS_CRED_ID = "736373_manageiq_up"

        // Credentials used to for node SSH
        NODE_DEFAULT_SSH_CRED  = credentials("vm-deployment-ssh-cred")
        NODE_USER = "${NODE_DEFAULT_SSH_CRED_USR}"
        NODE_PASS = "${NODE_DEFAULT_SSH_CRED_PSW}"
        CLUSTER_PASS = "${NODE_DEFAULT_SSH_CRED_PSW}"

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

        STAGE_DEPLOY = "yes"
    }

    stages {

        // Checkout cortx-re (build/deploy script repo) and cortx-s3server (source code repo)
        stage('Info') {
            steps {
				script { build_stage=env.STAGE_NAME }

                script {
                    
                    manager.addHtmlBadge("&emsp;<b>Build :</b> <a href=\"${CORTX_BUILD}\"><b>${PR_IDENTIFIER}</b></a> <br />")

                    sh """
                        set +x
                        echo "-------------- PR BUILD PARAMETERS -------------------"
                        echo "PR                = ${ghprbPullLink}"
                        echo "PR Commit         = ${ghprbActualCommit}"
                        echo "PR Author         = ${ghprbTriggerAuthor}"
                        echo "PR Title          = ${ghprbPullTitle}"
                        echo "-----------------------------------------------------------"
                    """
                }
            }
        }

        // Build s3server fromm PR source code
        stage('Build') {
            steps {
				script { build_stage=env.STAGE_NAME }
                 
                dir("cortx-s3server"){

                    checkout([$class: 'GitSCM', branches: [[name: "${ghprbActualCommit}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false], [$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${REPO_URL}",  name: 'origin', refspec: "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"]]])

                    sh label: 'prepare build env', script: """
                        sed '/baseurl/d' /etc/yum.repos.d/motr_current_build.repo
                        echo "baseurl=http://cortx-storage.colo.seagate.com/releases/cortx/components/github/${BRANCH}/${OS_VERSION}/dev/motr/current_build/"  >> /etc/yum.repos.d/motr_current_build.repo
                        yum clean all;rm -rf /var/cache/yum
                    """

                    sh label: 'Build s3server RPM', script: '''
                        yum clean all;rm -rf /var/cache/yum
                        export build_number=${BUILD_NUMBER}
                        yum install cortx-motr{,-devel} -y
                        yum erase log4cxx_eos-devel -q -y
                        ./rpms/s3/buildrpm.sh -S $VERSION -P $PWD -l
                        
                    '''
                    sh label: 'Build s3iamcli RPM', script: '''
                        export build_number=${BUILD_NUMBER}
                        ./rpms/s3iamcli/buildrpm.sh -S $VERSION -P $PWD
                    '''
                }
            }
        }

        // Release cortx deployment stack
        stage('Release') {
            steps {
				script { build_stage=env.STAGE_NAME }

                dir('cortx-re') {
                    checkout([$class: 'GitSCM', branches: [[name: '*/EOS-16376']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true],[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
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
                        cp /root/rpmbuild/RPMS/noarch/*.rpm "${CORTX_ISO_LOCATION}"
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

        // Deploy s3 mini provisioner 
        // 1. Intall prereq tools
        // 2. Install s3server and dependent component(motr,cortx-pyutils) from the provided build
        // 3. Execute s3 mini provisioning to configure the deployment attributes
        // 4. Start S3Server, Motr to perform I/O
        // 5. Validate the deployment by performing basic i/o using s3cli command
        // Ref - https://github.com/Seagate/cortx-s3server/wiki/S3server-provisioning-on-single-node-cluster:-Manual
        stage('Deploy'){
            agent { node { label "mini_provisioner" } }
            when { expression { env.STAGE_DEPLOY == "yes" } }
            steps {
                script {

                    // Cleanup Workspace
                    cleanWs()

                    manager.addHtmlBadge("&emsp;<b>Host :</b><a href='${JENKINS_URL}/computer/${env.NODE_NAME}'> ${NODE1_HOST}</a>")

                    // Run Deployment
                    catchError {
                        
                        dir('cortx-re') {
                            checkout([$class: 'GitSCM', branches: [[name: '*/EOS-16376']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true],[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
                        }

                        runAnsible("00_PREP_ENV, 01_PREREQ, 02_INSTALL_S3SERVER, 03_MINI_PROV, 04_START_S3SERVER, 05_VALIDATE")

                    }

                    // Collect logs from test node
                    catchError {

                        sh label: 'download_log_files', returnStdout: true, script: """ 
                            sshpass -p '${NODE_PASS}' scp -r -o StrictHostKeyChecking=no ${NODE_USER}@${NODE1_HOST}:/root/*.log .
                        """
                        
                        archiveArtifacts artifacts: "**/*.log", onlyIfSuccessful: false, allowEmptyArchive: true 
                    }

                    // Trigger cleanup VM
                    build job: 'Cortx-Deployment/VM-Cleanup', wait: false, parameters: [string(name: 'NODE_LABEL', value: "${env.NODE_NAME}")]
                    
                    // Create Summary
                    createSummary()

                    // Cleanup Workspace
                    cleanWs()
                }
            }
        }
	}
}	

// Method returns VM Host Information ( host, ssh cred)
def getTestMachine(host, user, pass){

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
def runAnsible(tags){
    withCredentials([usernamePassword(credentialsId: "${NODE_UN_PASS_CRED_ID}", passwordVariable: 'SERVICE_PASS', usernameVariable: 'SERVICE_USER'),usernameColonPassword(credentialsId: "${CLOUDFORM_TOKEN_CRED_ID}", variable: 'CLOUDFORM_API_CRED')]) {
        
        dir("cortx-re/scripts/components_deployment"){
            ansiblePlaybook(
                playbook: 's3server_deploy.yml',
                inventory: 'inventories/hosts',
                tags: "${tags}",
                extraVars: [
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

// Create Summary
def createSummary(){

    hctl_status=""
    if (fileExists('hctl_status.log')) {
        hctl_status=readFile(file: 'hctl_status.log')
        MESSAGE="S3Server Deployment Completed"
        ICON="accept.gif"
    }else{
        manager.buildFailure()
        MESSAGE = "S3Server Deployment Failed"
        ICON = "error.gif"
    }

    hctl_status_html="<textarea rows=20 cols=200 readonly style='margin: 0px; height: 392px; width: 843px;'>${hctl_status}</textarea>"
    table_summary="<table border='1' cellspacing='0' cellpadding='0' width='400' align='left'> <tr> <td align='center'>Build</td><td align='center'><a href=${CORTX_BUILD}>${build_id}</a></td></tr><tr> <td align='center'>Test HW</td><td align='center'>${NODE1_HOST}</td></tr></table>"
    manager.createSummary("${ICON}").appendText("<h3>${MESSAGE} for the build <a href=\"${CORTX_BUILD}\">${build_id}.</a></h3><p>Please check <a href=\"${CORTX_BUILD}/artifact/setup.log\">setup.log</a> for more info <br /><br /><h4>Test Details:</h4> ${table_summary} <br /><br /><br /><h4>HCTL Status:${hctl_status_html}</h4> ", false, false, false, "red")

}