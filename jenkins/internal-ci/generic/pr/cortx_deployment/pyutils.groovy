#!/usr/bin/env groovy
// CLEANUP REQUIRED
pipeline { 
    agent {
        node {
            label "docker-${OS_VERSION}-node"
        }
    }
    triggers { cron('30 18 * * *') }
    options { 
        skipDefaultCheckout()
        timeout(time: 180, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        ansiColor('xterm')  
    }

    parameters {  
	    string(name: 'PY_UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'Repo for Py-Utils Agent')
        string(name: 'PY_UTILS_BRANCH', defaultValue: 'main', description: 'Branch for Py-Utils Agent')
        string(name: 'CORTX_RE_URL', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repo for cortx-re')
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch for cortx-re')

        choice (
            choices: ['all', 'cortx-rgw', 'cortx-data', 'cortx-control'],
            description: 'CORTX Image to be built. Defaults to all images ',
            name: 'CORTX_IMAGE'
        )
        choice (
            choices: ['sanity','regression','audit_log','cli_framework','cmd_framework','conf_store','consul_service','iem_framework','kv_store','message_bus','support_bundle','validator'],
            description: 'Test plan to be executed for py-utils. Defaults to sanity',
            name: 'TEST_PLAN'
        )
    }

    environment {

        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        PY_UTILS_URL = "${ghprbGhRepository != null ? GPR_REPO : PY_UTILS_URL}"
        PY_UTILS_BRANCH = "${sha1 != null ? sha1 : PY_UTILS_BRANCH}"

        PY_UTILS_REFSPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        PY_UTILS_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        PY_UTILS_PR_REFSPEC = "${ghprbPullId != null ? PY_UTILS_REFSPEC : PY_UTILS_BRANCH_REFSEPEC}"

        //////////////////////////////// BUILD VARS //////////////////////////////////////////////////
        // OS_VERSION, host, COMPONENTS_BRANCH are manually created parameters in jenkins job.

        COMPONENT_NAME = "cortx-utils".trim()
        BRANCH = "${ghprbTargetBranch != null ? ghprbTargetBranch : COMPONENTS_BRANCH}"
        THIRD_PARTY_VERSION = "${OS_VERSION}-2.0.0-k8"
        PASSPHARASE = credentials('rpm-sign-passphrase')

        VERSION = "2.0.0" 

        OS_FAMILY = sh(script: "echo '${OS_VERSION}' | cut -d '-' -f1", returnStdout: true).trim()
        BUILD_TRIGGER_BY = "${currentBuild.getBuildCauses()[0].shortDescription}"
		
	    // Artifacts root location
		
	    DESTINATION_RELEASE_LOCATION = "/mnt/bigstorage/releases/cortx/github/pr-build/${BRANCH}/${COMPONENT_NAME}/${BUILD_NUMBER}"
        CORTX_BUILD = "http://cortx-storage.colo.seagate.com/releases/cortx/github/pr-build/${BRANCH}/${COMPONENT_NAME}/${BUILD_NUMBER}"
        PYTHON_DEPS = "/mnt/bigstorage/releases/cortx/third-party-deps/python-deps/python-packages-2.0.0-latest"
        THIRD_PARTY_DEPS = "/mnt/bigstorage/releases/cortx/third-party-deps/${OS_FAMILY}/${THIRD_PARTY_VERSION}/"
        COMPONENTS_RPM = "/mnt/bigstorage/releases/cortx/components/github/${BRANCH}/${OS_VERSION}/dev/"

        // Artifacts location
        CORTX_ISO_LOCATION = "${DESTINATION_RELEASE_LOCATION}/cortx_iso"
        THIRD_PARTY_LOCATION = "${DESTINATION_RELEASE_LOCATION}/3rd_party"
        PYTHON_LIB_LOCATION = "${DESTINATION_RELEASE_LOCATION}/python_deps"
		
        build_id = sh(script: "echo ${CORTX_BUILD} | rev | cut -d '/' -f2,3 | rev", returnStdout: true).trim()
        STAGE_DEPLOY = "yes"
        NODE_HOST = sh( script: '''
            echo $host | tr ' ' '\n' | head -1 | awk -F["="] '{print $2}' | cut -d',' -f1    
        ''', returnStdout: true).trim()
        NODE_USER = sh( script: '''
            echo $host | tr ' ' '\n' | head -1 | awk -F["="] '{print $3}' | cut -d',' -f1    
        ''', returnStdout: true).trim()
        NODE_PASS = sh( script: '''
            echo $host | tr ' ' '\n' | head -1 | awk -F["="] '{print $4}' | cut -d',' -f1    
        ''', returnStdout: true).trim()
    }

    stages {

        // Build py_utils fromm PR source code
        stage('Build') {
            steps {
				script { build_stage = env.STAGE_NAME }
                script { manager.addHtmlBadge("&emsp;<b>Target Branch : ${BRANCH}</b>&emsp;<br />") }

                sh """
                    set +x
                    echo "--------------BUILD PARAMETERS -------------------"
                    echo "PY_UTILS_URL              = ${PY_UTILS_URL}"
                    echo "PY_UTILS_BRANCH           = ${PY_UTILS_BRANCH}"
                    echo "PY_UTILS_PR_REFSPEC       = ${PY_UTILS_PR_REFSPEC}"
                    echo "-----------------------------------------------------------"
                """
                 
                dir("cortx_utils") {

                    checkout([$class: 'GitSCM', branches: [[name: "${PY_UTILS_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${PY_UTILS_URL}",  name: 'origin', refspec: "${PY_UTILS_PR_REFSPEC}"]]])

                    sh label: 'Build', script: '''
                        yum install python36-devel -y
                        ./jenkins/build.sh -v $VERSION -b $BUILD_NUMBER
                        ./statsd-utils/jenkins/build.sh -v $VERSION -b $BUILD_NUMBER
                        ./jenkins/build_test_rpm.sh -v $VERSION -b $BUILD_NUMBER    
                    '''
                }
            }
        }

        // Release cortx deployment stack
        stage('Release') {
            steps {
				script { build_stage = env.STAGE_NAME }
                echo "Creating Provisioner Release"
                dir('cortx-re') {
                    checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true], [$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
                }

                // Install tools required for release process
                sh label: 'Installed Dependecies', script: '''
                    yum install -y expect rpm-sign rng-tools python3-pip
                    #if [ "${OS_FAMILY}" == "rockylinux" ]
                    #then
                    #    ln -fs $(which python3.6) /usr/bin/python2
                    #else
                    #    echo "Using CentOS"
                    #fi
                    #systemctl start rngd
                '''

                // Integrate components rpms
                sh label: 'Collect Release Artifacts', script: '''
                    set -x 
                    echo -e "Gathering all component RPM's and create release"
                    rm -rf "${DESTINATION_RELEASE_LOCATION}"
                    mkdir -p "${DESTINATION_RELEASE_LOCATION}"
                    mkdir -p "${CORTX_ISO_LOCATION}"
                    shopt -s extglob
                    ls
                    cp ./cortx_utils/py-utils/dist/!(*.src.rpm|*.tar.gz) "${CORTX_ISO_LOCATION}"
                    cp ./cortx_utils/py-utils/test/dist/!(*.src.rpm|*.tar.gz) "${CORTX_ISO_LOCATION}"
                    cp ./cortx_utils/statsd-utils/dist/rpmbuild/RPMS/x86_64/*.rpm "${CORTX_ISO_LOCATION}"
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
                
                sh label: 'Create Repo', script: '''
                    pushd ${CORTX_ISO_LOCATION}
                        yum install -y createrepo
                        createrepo .
                    popd
                '''	

                sh label: 'Generate RELEASE.INFO', script: '''
                    echo -e "Creating release information files"
                    pushd cortx-re/scripts/release_support
                        sh build_release_info.sh -v ${VERSION} -l ${CORTX_ISO_LOCATION} -t ${THIRD_PARTY_LOCATION}
                        sed -i -e 's/BRANCH:.*/BRANCH: "py-utils-pr"/g' ${CORTX_ISO_LOCATION}/RELEASE.INFO
                        sh build_readme.sh "${DESTINATION_RELEASE_LOCATION}"
                    popd
                    cp "${THIRD_PARTY_LOCATION}/THIRD_PARTY_RELEASE.INFO" "${DESTINATION_RELEASE_LOCATION}"
                    cp "${CORTX_ISO_LOCATION}/RELEASE.INFO" "${DESTINATION_RELEASE_LOCATION}"
                    cp "${CORTX_ISO_LOCATION}/RELEASE.INFO" .
                '''	

                archiveArtifacts artifacts: "RELEASE.INFO", onlyIfSuccessful: false, allowEmptyArchive: true	
            }

        }

        // Deploy Cortx-Stack
        stage ("Build CORTX Images") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        def buildCortxAllImage = build job: 'cortx-docker-images-for-PR', wait: true,
                            parameters: [
                                string(name: 'CORTX_RE_URL', value: "${CORTX_RE_URL}"),
                                string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                                string(name: 'BUILD', value: "${CORTX_BUILD}"),
                                string(name: 'OS', value: "${OS_VERSION}"),
                                string(name: 'CORTX_IMAGE', value: "${CORTX_IMAGE}"),
                                string(name: 'GITHUB_PUSH', value: "yes"),
                                string(name: 'TAG_LATEST', value: "no"),
                                string(name: 'DOCKER_REGISTRY', value: "cortx-docker.colo.seagate.com"),
                                string(name: 'EMAIL_RECIPIENTS', value: "DEBUG")
                            ]
                        env.cortx_rgw_image = buildCortxAllImage.buildVariables.cortx_rgw_image
                        env.cortx_data_image = buildCortxAllImage.buildVariables.cortx_data_image
                        env.cortx_control_image = buildCortxAllImage.buildVariables.cortx_control_image
                        env.image = buildCortxAllImage.buildVariables.image
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build CORTX-ALL image"
                    }
                }
            }
        }

        stage ("Deploy Cortx Cluster") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    build job: "K8s-1N-deployment", wait: true,
                    parameters: [
                        string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_URL}"),
                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                        string(name: 'CORTX_SERVER_IMAGE', value: "${env.cortx_rgw_image}"),
                        string(name: 'CORTX_DATA_IMAGE', value: "${env.cortx_data_image}"),
                        string(name: 'CORTX_CONTROL_IMAGE', value: "${env.cortx_control_image}"),
                        string(name: 'CORTX_SCRIPTS_REPO', value: "Seagate/cortx-k8s"),
                        string(name: 'hosts', value: "${host}"),
                        string(name: 'EMAIL_RECIPIENTS', value: "DEBUG")
                    ]
                }
            }
        }

        stage ("Test Utils") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    sh '''
 	                    yum install -y sshpass
 	                    sshpass -p $NODE_PASS ssh -o StrictHostKeyChecking=no $NODE_USER@$NODE_HOST env TEST_PLAN="$TEST_PLAN" BUILD_NUMBER="$BUILD_NUMBER" 'bash -s' <<'EOF'
                        kubectl exec $(kubectl get pods | awk '/cortx-data/{print $1; exit}') --container cortx-hax -- sh -c "yum install -y wget yum-utils \
                            && yum-config-manager --add-repo http://cortx-storage.colo.seagate.com/releases/cortx/github/pr-build/main/cortx-utils/$BUILD_NUMBER/cortx_iso \
                            && yum install --nogpgcheck -y cortx-py-utils-test-* \
                            && /opt/seagate/cortx/utils/bin/utils_setup test --config yaml:///etc/cortx/cluster.conf --plan $TEST_PLAN"
                        kubectl exec $(kubectl get pods | awk '/cortx-data/{print $1; exit}') --container cortx-hax -- bash -c 'cat /tmp/py_utils_test_report.html' | tee py_utils_test_report.html    
EOF
	 	            '''
	 	            def remote = getRemoteMachine(NODE_HOST, NODE_USER, NODE_PASS)
                    sshGet remote: remote, from: '/root/py_utils_test_report.html', into: 'py_utils_test_report.html', override: true
                }
            }
        }
	}

    post {
        always {
            script {
                if (fileExists('py_utils_test_report.html')) {
                    def testStatus = sh( script: "grep Overall < py_utils_test_report.html | sed 's/<[^>]*>//g' | awk -F[':'] '{ print \$2 }' | tr -d '[:space:]'", returnStdout: true)
                    if ( "${testStatus}" == "Failed" ) {
                        currentBuild.result = "FAILURE"
                    }
                    archiveArtifacts allowEmptyArchive: true, artifacts: 'py_utils_test_report.html', followSymlinks: false    
                } else {
                    echo 'ERROR : py_utils_test_report.html is not present'
                }

                env.build_stage = "${build_stage}"
                env.test_report_url = sh( script: "echo ${BUILD_URL}/artifact/py_utils_test_report.html", returnStdout: true)

                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]

                if ( env.BUILD_TRIGGER_BY == 'Started by timer' ) {
                    emailext ( 
                        body: '''${SCRIPT, template="pr-deployment-email.template"}''',
                        mimeType: 'text/html',
                        subject: "[Nightly Py-Utils Build] : ${currentBuild.currentResult}",
                        attachLog: true,
                        to: "CORTX.Foundation@seagate.com",
                        recipientProviders: recipientProvidersClass
                    )
                }    

                sh label: 'Remove artifacts', script: '''rm -rf "${DESTINATION_RELEASE_LOCATION}"'''
            }
        }
        failure {
            script {
                manager.addShortText("${build_stage} Failed")
            }  
        }
    }
}

def getRemoteMachine(String host, String VM_USR, String VM_PWD) {
    def remote = [:]
    remote.name = 'cortx-vm'
    remote.host = host
    remote.user =  VM_USR
    remote.password = VM_PWD
    remote.allowAnyHosts = true
    remote.fileTransfer = 'scp'
    return remote
}