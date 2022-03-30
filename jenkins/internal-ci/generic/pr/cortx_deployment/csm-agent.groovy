#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            label "docker-${OS_VERSION}-node"
        }
    }

    options { 
        skipDefaultCheckout()
        timeout(time: 180, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        ansiColor('xterm')  
    }

    parameters {  
        string(name: 'CSM_URL', defaultValue: 'https://github.com/Seagate/cortx-manager', description: 'Repo for CSM Agent')
        string(name: 'CSM_BRANCH', defaultValue: 'main', description: 'Branch for CSM Agent') 
        string(name: 'CORTX_RE_URL', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repo for cortx-re')
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch for cortx-re')

        choice (
            choices: ['all', 'cortx-all' , 'cortx-rgw', 'cortx-data'],
            description: 'CORTX Image to be built. Defaults to all images ',
            name: 'CORTX_IMAGE'
        )
    }

    environment {

        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        CSM_URL = "${ghprbGhRepository != null ? GPR_REPO : CSM_URL}"
        CSM_BRANCH = "${sha1 != null ? sha1 : CSM_BRANCH}"

        CSM_GPR_REFSEPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        CSM_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        CSM_PR_REFSEPEC = "${ghprbPullId != null ? CSM_GPR_REFSEPEC : CSM_BRANCH_REFSEPEC}"

        //////////////////////////////// BUILD VARS //////////////////////////////////////////////////
        // OS_VERSION, host, COMPONENTS_BRANCH and CORTX_SCRIPTS_BRANCH are manually created parameters in jenkins job.

        COMPONENT_NAME = "csm-agent".trim()
        BRANCH = "${ghprbTargetBranch != null ? ghprbTargetBranch : COMPONENTS_BRANCH}"
        THIRD_PARTY_VERSION = "${OS_VERSION}-2.0.0-k8"
        VERSION = "2.0.0"
        RELEASE_TAG = "last_successful_prod"
        PASSPHARASE = credentials('rpm-sign-passphrase')

        OS_FAMILY=sh(script: "echo '${OS_VERSION}' | cut -d '-' -f1", returnStdout: true).trim()
        // OS_FAMILY="${echo '${OS_VERSION}' | cut -d '-' -f1 }"
        // Artifacts root location

        // 'WARNING' - rm -rf command used on this path please careful when updating this value
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

        STAGE_DEPLOY = "yes"
    }

    stages {

        // Build csm fromm PR source code
        stage('Build') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script { manager.addHtmlBadge("&emsp;<b>Target Branch : ${BRANCH}</b>&emsp;<br />") } 

                dir ('cortx-re') {
                    checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', noTags: true, reference: '', shallow: true]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]]
                }

                sh label: 'Setup Repositories', script: '''
                    #Use main branch for cortx-py-utils
                    yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/$BRANCH/$OS_VERSION/$RELEASE_TAG/cortx_iso/
                    yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*
                    yum clean all && rm -rf /var/cache/yum

                    pip3 install --no-cache-dir --trusted-host cortx-storage.colo.seagate.com -i http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/python-deps/python-packages-2.0.0-latest/ -r https://raw.githubusercontent.com/Seagate/cortx-utils/main/py-utils/python_requirements.txt -r https://raw.githubusercontent.com/Seagate/cortx-utils/main/py-utils/python_requirements.ext.txt

                    # Install pyinstaller    
                    pip3.6 install  pyinstaller==3.5
                '''
                 
                dir("cortx-manager") {

                    checkout([$class: 'GitSCM', branches: [[name: "${CSM_BRANCH}"]], doGenerateSubmoduleConfigurations: false,  extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CSM_URL}",  name: 'origin', refspec: "${CSM_PR_REFSEPEC}"]]])
                    // Exclude return code check for csm_setup and csm_test
                    sh label: 'Build', returnStatus: true, script: '''
                        BUILD=$(git rev-parse --short HEAD)
                        echo "Executing build script"
                        if [ ${OS_FAMILY} == "Centos" ]
                        then
                            echo "Python:$(python --version)"
                        else
                            echo "Python:$(python3 --version)"
                        fi
                        ./cicd/build.sh -v $VERSION -b $BUILD_NUMBER -t
                    '''

                    sh label: 'Collect Release Artifacts', script: '''
                    
                        rm -rf "${DESTINATION_RELEASE_LOCATION}"
                        mkdir -p "${DESTINATION_RELEASE_LOCATION}"
            
                        if [[ ( ! -z `ls ./dist/rpmbuild/RPMS/x86_64/*.rpm `)]]; then
                            mkdir -p "${CORTX_ISO_LOCATION}"
                            cp ./dist/rpmbuild/RPMS/x86_64/*.rpm "${CORTX_ISO_LOCATION}"
                        else
                            echo "RPM not exists !!!"
                            exit 1
                        fi
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
                    yum install -y expect rpm-sign rng-tools python3-pip
                   # ln -fs $(which python3.6) /usr/bin/python2
                   # systemctl start rngd
                '''

                // Integrate components rpms
                sh label: 'Collect Release Artifacts', script: '''
                    
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

                // sh label: 'RPM Signing', script: '''
                //     pushd cortx-re/scripts/rpm-signing
                //         cat gpgoptions >>  ~/.rpmmacros
                //         sed -i 's/passphrase/'${PASSPHARASE}'/g' genkey-batch
                //         gpg --batch --gen-key genkey-batch
                //         gpg --export -a 'Seagate'  > RPM-GPG-KEY-Seagate
                //         rpm --import RPM-GPG-KEY-Seagate
                //     popd


                //     pushd cortx-re/scripts/rpm-signing
                //         chmod +x rpm-sign.sh
                //         cp RPM-GPG-KEY-Seagate ${CORTX_ISO_LOCATION}
                //         for rpm in `ls -1 ${CORTX_ISO_LOCATION}/*.rpm`
                //         do
                //             ./rpm-sign.sh ${PASSPHARASE} ${rpm}
                //         done
                //     popd

                // '''
                
                sh label: 'Create REPO', script: '''
                    pushd ${CORTX_ISO_LOCATION}
                        yum install -y createrepo
                        createrepo .
                    popd
                '''    

                sh label: 'Generate RELEASE.INFO', script: '''
                    pushd cortx-re/scripts/release_support
                        sh build_readme.sh "${DESTINATION_RELEASE_LOCATION}"
                        sh build_release_info.sh -v ${VERSION} -l ${CORTX_ISO_LOCATION} -t ${THIRD_PARTY_LOCATION}
                        sed -i -e 's/BRANCH:.*/BRANCH: "csm-agent-pr"/g' ${CORTX_ISO_LOCATION}/RELEASE.INFO
                    popd

                    cp "${THIRD_PARTY_LOCATION}/THIRD_PARTY_RELEASE.INFO" "${DESTINATION_RELEASE_LOCATION}"
                    cp "${CORTX_ISO_LOCATION}/RELEASE.INFO" "${DESTINATION_RELEASE_LOCATION}"

                    cp "${CORTX_ISO_LOCATION}/RELEASE.INFO" .
                '''    

                archiveArtifacts artifacts: "RELEASE.INFO", onlyIfSuccessful: false, allowEmptyArchive: true    
            }

        }

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
                        env.cortx_all_image = buildCortxAllImage.buildVariables.cortx_all_image
                        env.cortx_rgw_image = buildCortxAllImage.buildVariables.cortx_rgw_image
                        env.cortx_data_image = build_cortx_images.buildVariables.cortx_data_image
                        env.cortx_control_image = build_cortx_images.buildVariables.cortx_control_image
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
                        string(name: 'CORTX_RE_URL', value: "${CORTX_RE_URL}"),
                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                        string(name: 'CORTX_ALL_IMAGE', value: "${env.cortx_all_image}"),
                        string(name: 'CORTX_SERVER_IMAGE', value: "${env.cortx_rgw_image}"),
                        string(name: 'CORTX_DATA_IMAGE', value: "${env.cortx_data_image}"),
                        string(name: 'CORTX_SCRIPTS_REPO', value: "Seagate/cortx-k8s"),
                        string(name: 'CORTX_SCRIPTS_BRANCH', value: "${CORTX_SCRIPTS_BRANCH}"),
                        string(name: 'hosts', value: "${host}"),
                        string(name: 'EMAIL_RECIPIENTS', value: "DEBUG")
                    ]
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