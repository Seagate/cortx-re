pipeline { 
    agent {
        node {
            label "ceph-build-node"
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
        string(name: 'RGW_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw', description: 'Repo for rgw')
        string(name: 'RGW_BRANCH', defaultValue: 'main', description: 'Branch for rgw')
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
        RGW_URL = "${ghprbGhRepository != null ? GPR_REPO : RGW_URL}"
        RGW_BRANCH = "${sha1 != null ? sha1 : RGW_BRANCH}"
        RGW_GPR_REFSEPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        RGW_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        RGW_PR_REFSEPEC = "${ghprbPullId != null ? RGW_GPR_REFSEPEC : RGW_BRANCH_REFSEPEC}"
        //////////////////////////////// BUILD VARS //////////////////////////////////////////////////
        // OS_VERSION, singlenode_host, threenode_hosts, COMPONENTS_BRANCH and CORTX_SCRIPTS_BRANCH are manually created parameters in jenkins job.
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
                    echo "RGW_URL              = ${RGW_URL}"
                    echo "RGW_BRANCH           = ${RGW_BRANCH}"
                    echo "RGW_PR_REFSEPEC      = ${RGW_PR_REFSEPEC}"
                    echo "-----------------------------------------------------------"
                """
                dir ('cortx-rgw') {
                    checkout([$class: 'GitSCM', branches: [[name: "${RGW_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${RGW_URL}",  name: 'origin', refspec: "${RGW_PR_REFSEPEC}"]]])
                }
                sh label: 'Build', script: '''                    
                    ls -lrt
                    pushd cortx-rgw
                        ./install-deps.sh
                        ./make-dist
                        mkdir -p $RELEASE_DIR/$COMPONENT_NAME/$BRANCH/rpmbuild/$BUILD_NUMBER/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
                        mv ceph*.tar.bz2 $RELEASE_DIR/$COMPONENT_NAME/$BRANCH/rpmbuild/$BUILD_NUMBER/SOURCES/
                        tar --strip-components=1 -C $RELEASE_DIR/$COMPONENT_NAME/$BRANCH/rpmbuild/$BUILD_NUMBER/SPECS/ --no-anchored -xvjf $RELEASE_DIR/$COMPONENT_NAME/$BRANCH/rpmbuild/$BUILD_NUMBER/SOURCES/ceph*.tar.bz2 "ceph.spec"
                    popd
                '''
                sh label: 'Configure yum repositories', script: """
                    set +x
                    yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/$BRANCH/$OS_VERSION/$RELEASE_TAG/cortx_iso/
                    yum clean all;rm -rf /var/cache/yum
                    yum install cortx-motr{,-devel} -y --nogpgcheck
                """
                sh label: 'Build cortx-rgw packages', script: '''
                    pushd $RELEASE_DIR/$COMPONENT_NAME/$BRANCH/rpmbuild/$BUILD_NUMBER
                        rpmbuild --clean --rmsource --define "_unpackaged_files_terminate_build 0" --define "debug_package %{nil}" --without cmake_verbose_logging --without jaeger --without lttng --without seastar --without kafka_endpoint --without zbd --without cephfs_java --without cephfs_shell --without ocf --without selinux --without ceph_test_package --without make_check --define "_binary_payload w2T16.xzdio" --define "_topdir `pwd`" -ba ./SPECS/ceph.spec
                    popd    
                '''
                sh label: 'Copy RPMS', script: '''
                    rm -rvf /root/build_rpms     
                    mkdir -p "/root/build_rpms"
                    cp $RELEASE_DIR/$COMPONENT_NAME/$BRANCH/rpmbuild/$BUILD_NUMBER/RPMS/*/*.rpm /root/build_rpms
                '''
                sh label: 'Repo Creation', script: '''pushd /root/build_rpms
                    rpm -qi createrepo || yum install -y createrepo
                    createrepo .
                    popd
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