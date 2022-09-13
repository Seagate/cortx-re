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
        string(name: 'PROVISIONER_URL', defaultValue: 'https://github.com/Seagate/cortx-prvsnr.git', description: 'Repo for PROVISIONER Agent')
        string(name: 'PROVISIONER_BRANCH', defaultValue: 'main', description: 'Branch for PROVISIONER Agent')
        string(name: 'CORTX_RE_URL', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repo for cortx-re')
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch for cortx-re')

        choice (
            choices: ['all', 'cortx-rgw', 'cortx-data', 'cortx-control'],
            description: 'CORTX Image to be built. Defaults to all images ',
            name: 'CORTX_IMAGE'
        )
    }
    environment {

        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        PROVISIONER_URL = "${ghprbGhRepository != null ? GPR_REPO : PROVISIONER_URL}"
        PROVISIONER_BRANCH = "${sha1 != null ? sha1 : PROVISIONER_BRANCH}"

        PROVISIONER_GPR_REFSPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        PROVISIONER_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        PROVISIONER_PR_REFSPEC = "${ghprbPullId != null ? PROVISIONER_GPR_REFSPEC : PROVISIONER_BRANCH_REFSEPEC}"
        
        //////////////////////////////// BUILD VARS //////////////////////////////////////////////////
        // OS_VERSION, host, COMPONENTS_BRANCH are manually created parameters in jenkins job.

        VERSION = "2.0.0"
        COMPONENT_NAME = "provisioner".trim()
        BRANCH = "${ghprbTargetBranch != null ? ghprbTargetBranch : COMPONENTS_BRANCH}"
        THIRD_PARTY_VERSION = "${OS_VERSION}-2.0.0-k8"
        PASSPHARASE = credentials('rpm-sign-passphrase')
		OS_FAMILY = sh(script: "echo '${OS_VERSION}' | cut -d '-' -f1", returnStdout: true).trim()

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
    }
    stages {
        stage('Build') {
            steps {
				script { build_stage = env.STAGE_NAME }
                script { manager.addHtmlBadge("&emsp;<b>Target Branch : ${BRANCH}</b>&emsp;<br />") }
				
                sh """
                    set +x
                    echo "--------------BUILD PARAMETERS -------------------"
                    echo "PROVISIONER_URL              = ${PROVISIONER_URL}"
                    echo "PROVISIONER_BRANCH           = ${PROVISIONER_BRANCH}"
                    echo "PROVISIONER_PR_REFSPEC       = ${PROVISIONER_PR_REFSPEC}"
                    echo "-----------------------------------------------------------"
                """

					
				checkout([$class: 'GitSCM', branches: [[name: "${PROVISIONER_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${PROVISIONER_URL}", refspec: "${PROVISIONER_PR_REFSPEC}"]]])
            
                sh encoding: 'UTF-8', label: 'cortx-provisioner', script: '''
                    if [ -f "./jenkins/build.sh" ]; then
                        bash ./jenkins/build.sh -v 2.0.0 -b ${BUILD_NUMBER}
                    else
                        echo "cortx-provisioner package creation is not implemented"
                    fi
                '''

                sh label: 'Repo Creation', script: '''mkdir -p $DESTINATION_RELEASE_LOCATION
                    pushd $DESTINATION_RELEASE_LOCATION
                    yum install -y createrepo
                    createrepo .
                    popd
                '''
					
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
                  #  ln -fs $(which python3.6) /usr/bin/python2
                  #  systemctl start rngd
                '''

                // Integrate components rpms
                sh label: 'Collect Release Artifacts', script: '''
                    echo -e "Gathering all component RPM's and create release"
                    rm -rf "${DESTINATION_RELEASE_LOCATION}"
                    mkdir -p "${DESTINATION_RELEASE_LOCATION}"
                    shopt -s extglob
                    if ls ./dist/*.rpm; then
                        mkdir -p "${CORTX_ISO_LOCATION}"
                        cp ./dist/!(*.src.rpm|*.tar.gz) "${CORTX_ISO_LOCATION}"
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
                
                sh label: 'Create Repo', script: '''
                    pushd ${CORTX_ISO_LOCATION}
                        rpm -qi createrepo || yum install -y createrepo
                        createrepo .
                    popd
                '''	

                sh label: 'Generate RELEASE.INFO', script: '''
                    echo -e "Creating release information files"
                    pushd cortx-re/scripts/release_support
                        sh build_release_info.sh -v ${VERSION} -l ${CORTX_ISO_LOCATION} -t ${THIRD_PARTY_LOCATION}
                        sed -i -e 's/BRANCH:.*/BRANCH: "provisioner-pr"/g' ${CORTX_ISO_LOCATION}/RELEASE.INFO
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
                        error "Failed to Build CORTX Images"
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