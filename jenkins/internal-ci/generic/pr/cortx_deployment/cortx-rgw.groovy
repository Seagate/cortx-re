pipeline { 
    agent {
        node {
            label "ceph-build-hw"
        }
    }
    options { 
        skipDefaultCheckout()
        timeout(time: 360, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        ansiColor('xterm')  
    }
    parameters {  
        string(name: 'CORTX_RGW_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw', description: 'Repo for rgw')
        string(name: 'CORTX_RGW_BRANCH', defaultValue: 'main', description: 'Branch for rgw')
        string(name: 'CORTX_RE_URL', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repo for cortx-re')
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch for cortx-re')
        choice (
            choices: ['all', 'cortx-control', 'cortx-rgw', 'cortx-data'],
            description: 'CORTX Image to be built. Defaults to all images ',
            name: 'CORTX_IMAGE'
        )
    }
    environment {
        // rgw Repo Info
        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        CORTX_RGW_URL = "${ghprbGhRepository != null ? GPR_REPO : CORTX_RGW_URL}"
        CORTX_RGW_BRANCH = "${sha1 != null ? sha1 : CORTX_RGW_BRANCH}"
        RGW_GPR_REFSEPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        CORTX_RGW_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        CORTX_RGW_PR_REFSEPEC = "${ghprbPullId != null ? CORTX_RGW_GPR_REFSEPEC : CORTX_RGW_BRANCH_REFSEPEC}"
        //////////////////////////////// BUILD VARS //////////////////////////////////////////////////
        // OS_VERSION, singlenode_host, threenode_hosts, COMPONENTS_BRANCH and CORTX_SCRIPTS_BRANCH are manually created parameters in jenkins job.
        BUILD_LOCATION = "/var/log/pr-build/cortx-rgw-build/${BUILD_NUMBER}"
        BUILD_OS = "${OS_VERSION}"
        CORTX_RGW_OPTIMIZED_BUILD = true
        INSTALL_MOTR = true
        PR_BUILD = true
        PR_ID = "${ghprbPullId}"
        COMPONENT_NAME = "cortx-rgw".trim()
        BRANCH = "${ghprbTargetBranch != null ? ghprbTargetBranch : COMPONENTS_BRANCH}"
        THIRD_PARTY_VERSION = "${OS_VERSION}-2.0.0-k8"
        VERSION = "2.0.0"
        RELEASE_TAG = "last_successful_prod"
        PASSPHARASE = credentials('rpm-sign-passphrase')
        RELEASE_DIR = "/mnt/bigstorage/releases/cortx"
        OS_FAMILY=sh(script: "echo '${OS_VERSION}' | cut -d '-' -f1", returnStdout: true).trim()
        // 'WARNING' - rm -rf command used on DESTINATION_RELEASE_LOCATION path please careful when updating this value
        DESTINATION_RELEASE_LOCATION = "/mnt/bigstorage/releases/cortx/github/pr-build/${BRANCH}/${COMPONENT_NAME}/${BUILD_NUMBER}"
        PYTHON_DEPS = "/mnt/bigstorage/releases/cortx/third-party-deps/python-deps/python-packages-2.0.0-latest"
        THIRD_PARTY_DEPS = "/mnt/bigstorage/releases/cortx/third-party-deps/${OS_FAMILY}/${THIRD_PARTY_VERSION}/"
        COMPONENTS_RPM = "/mnt/bigstorage/releases/cortx/components/github/${BRANCH}/${OS_VERSION}/dev/"
        CORTX_BUILD = "http://cortx-storage.colo.seagate.com/releases/cortx/github/pr-build/${BRANCH}/${COMPONENT_NAME}/${BUILD_NUMBER}"
        // Artifacts location
        CORTX_ISO_LOCATION = "${DESTINATION_RELEASE_LOCATION}/cortx_iso"
        THIRD_PARTY_LOCATION = "${DESTINATION_RELEASE_LOCATION}/3rd_party"
        PYTHON_LIB_LOCATION = "${DESTINATION_RELEASE_LOCATION}/python_deps"
        ////////////////////////////////// DEPLOYMENT VARS /////////////////////////////////////////////////////
        // NODE1_HOST - Env variables added in the node configurations
        build_identifier = sh(script: "echo ${CORTX_BUILD} | rev | cut -d '/' -f2,3 | rev", returnStdout: true).trim()
        STAGE_DEPLOY = "yes"
    }
    stages {
        // Build rgw from PR source code
        stage('Build') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script { manager.addHtmlBadge("&emsp;<b>Target Branch : ${BRANCH}</b>&emsp;<br />") }
                 sh """
                    set +x
                    echo "--------------BUILD PARAMETERS -------------------"
                    echo "CORTX_RGW_URL              = ${CORTX_RGW_URL}"
                    echo "CORTX_RGW_BRANCH           = ${CORTX_RGW_BRANCH}"
                    echo "CORTX_RGW_PR_REFSEPEC      = ${CORTX_RGW_PR_REFSEPEC}"
                    echo "-----------------------------------------------------------"
                """

                dir('cortx-re') {
                    checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true], [$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
                }

                sh label: 'Build', script: """
                    pushd cortx-re/solutions/kubernetes/
                        export CEPH_REPO=${CORTX_RGW_URL}
                        export CEPH_BRANCH=${CORTX_RGW_BRANCH}
                        export os_version=${OS_VERSION}
                        export branch=${branch}
                        export release_tag=${RELEASE_TAG}
                        bash ceph-binary-build.sh --ceph-build-env ${BUILD_LOCATION}
                    popd
                """

                sh label: 'Copy RPMS', script: '''
                    rm -rvf /root/build_rpms
                    mkdir -p "/root/build_rpms"
                    pushd $BUILD_LOCATION/$OS_VERSION/rpmbuild
                        cp RPMS/*/*.rpm /root/build_rpms
                    popd
                '''

                sh label: 'Repo Creation', script: '''
                    pushd /root/build_rpms
                        rpm -qi createrepo || yum install -y createrepo
                        createrepo .
                    popd
                '''

                sh label: 'Remove Build Location', script: '''
                    rm -rf $BUILD_LOCATION
                '''
            }
        }
        // Release cortx deployment stack
        stage('Release') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir('cortx-re') {
                    checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true], [$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
                }
                //Install tools required for release process
                sh label: 'Installed Dependecies', script: '''
                    yum install -y expect rpm-sign rng-tools python3-pip
                    # ln -fs $(which python3.6) /usr/bin/python2 
                    # systemctl start rngd
                '''
                // Integrate components rpms
                sh label: 'Collect Release Artifacts', script: '''
                    rm -rf "${DESTINATION_RELEASE_LOCATION}"
                    mkdir -p "${DESTINATION_RELEASE_LOCATION}"
                    if [[ ( ! -z `ls /root/build_rpms/*.rpm `)]]; then
                        mkdir -p "${CORTX_ISO_LOCATION}"
                        cp /root/build_rpms/*.rpm "${CORTX_ISO_LOCATION}"
                    else
                        echo "RPM not exists !!!"
                        exit 1
                    fi 
                    pushd ${COMPONENTS_RPM}
                        for component in `ls -1 | grep -v "cortx-rgw"$`
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
                sh label: 'Repo Creation', script: '''
                    pushd ${CORTX_ISO_LOCATION}
                        yum install -y createrepo
                        createrepo .
                    popd
                ''' 
                sh label: 'Generate RELEASE.INFO', script: '''
                    pushd cortx-re/scripts/release_support
                        sh build_release_info.sh -v ${VERSION} -l ${CORTX_ISO_LOCATION} -t ${THIRD_PARTY_LOCATION}
                        sed -i -e 's/BRANCH:.*/BRANCH: "cortx-rgw-pr"/g' ${CORTX_ISO_LOCATION}/RELEASE.INFO
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
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build CORTX-ALL image"
                    }
                }
            }
        }
        stage ('Deploy Cortx Cluster') {
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
                        string(name: 'hosts', value: "${singlenode_host}"),
                        string(name: 'EMAIL_RECIPIENTS', value: "DEBUG")
                    ] 
                }
            }
        }                 
    }
    post {
        always {
            script {
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