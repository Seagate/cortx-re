#!/usr/bin/env groovy
// CLEANUP REQUIRED
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
	    string(name: 'S3_URL', defaultValue: 'https://github.com/Seagate/cortx-s3server', description: 'Repo for S3Server')
        string(name: 'S3_BRANCH', defaultValue: 'main', description: 'Branch for S3Server')
        choice(name: 'MESSAGING_PLATFORM', choices: ["rabbit_mq", "message_bus"], description: 'MESSAGING_PLATFORM')  
	}

    environment {

        // S3Server Repo Info

        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        S3_URL = "${ghprbGhRepository != null ? GPR_REPO : S3_URL}"
        S3_BRANCH = "${sha1 != null ? sha1 : S3_BRANCH}"

        S3_GPR_REFSEPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        S3_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        S3_PR_REFSEPEC = "${ghprbPullId != null ? S3_GPR_REFSEPEC : S3_BRANCH_REFSEPEC}"

        //////////////////////////////// BUILD VARS //////////////////////////////////////////////////
        // OS_VERSION, host and COMPONENTS_BRANCH are manually created parameters in jenkins job.

        COMPONENT_NAME = "s3server".trim()
        BRANCH = "${ghprbTargetBranch != null ? ghprbTargetBranch : COMPONENTS_BRANCH}"
        THIRD_PARTY_VERSION = "${OS_VERSION}-2.0.0-k8"
        VERSION = "2.0.0"
        PASSPHARASE = credentials('rpm-sign-passphrase')

        // Artifacts root location

        // 'WARNING' - rm -rf command used on this path please careful when updating this value
        DESTINATION_RELEASE_LOCATION = "/mnt/bigstorage/releases/cortx/github/pr-build/${BRANCH}/${COMPONENT_NAME}/${BUILD_NUMBER}"
        PYTHON_DEPS = "/mnt/bigstorage/releases/cortx/third-party-deps/python-deps/python-packages-2.0.0-latest"
        THIRD_PARTY_DEPS = "/mnt/bigstorage/releases/cortx/third-party-deps/centos/${THIRD_PARTY_VERSION}/"
        COMPONENTS_RPM = "/mnt/bigstorage/releases/cortx/components/github/${BRANCH}/${OS_VERSION}/dev/"
        CORTX_BUILD = "http://cortx-storage.colo.seagate.com/releases/cortx/github/pr-build/${BRANCH}/${COMPONENT_NAME}/${BUILD_NUMBER}"

        // Artifacts location
        CORTX_ISO_LOCATION = "${DESTINATION_RELEASE_LOCATION}/cortx_iso"
        THIRD_PARTY_LOCATION = "${DESTINATION_RELEASE_LOCATION}/3rd_party"
        PYTHON_LIB_LOCATION = "${DESTINATION_RELEASE_LOCATION}/python_deps"

        ////////////////////////////////// DEPLOYMENT VARS /////////////////////////////////////////////////////

        build_id = sh(script: "echo ${CORTX_BUILD} | rev | cut -d '/' -f2,3 | rev", returnStdout: true).trim()

        STAGE_DEPLOY = "yes"
    }

    stages {

        // Build s3server fromm PR source code
        stage('Build') {

            steps {

				script { build_stage = env.STAGE_NAME }
                script { manager.addHtmlBadge("&emsp;<b>Target Branch : ${BRANCH}</b>&emsp;<br />") }

                sh """
                    set +x
                    echo "--------------BUILD PARAMETERS -------------------"
                    echo "S3_URL              = ${S3_URL}"
                    echo "S3_BRANCH           = ${S3_BRANCH}"
                    echo "S3_PR_REFSEPEC      = ${S3_PR_REFSEPEC}"
                    echo "-----------------------------------------------------------"
                """
                 
                dir("cortx-s3server") {

                    checkout([$class: 'GitSCM', branches: [[name: "${S3_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false], [$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${S3_URL}",  name: 'origin', refspec: "${S3_PR_REFSEPEC}"]]])

                    sh label: 'prepare build env', script: """
                        yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/components/github/$BRANCH/$OS_VERSION/dev/motr/current_build/
                        yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*
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
				script { build_stage = env.STAGE_NAME }

                dir('cortx-re') {
                    checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true], [$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
                }

                // Install tools required for release process
                sh label: 'Installed Dependecies', script: '''
                    yum install -y expect rpm-sign rng-tools python3-pip
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
                        sh build_release_info.sh -v ${VERSION} -l ${CORTX_ISO_LOCATION} -t ${THIRD_PARTY_LOCATION}
                        sed -i -e 's/BRANCH:.*/BRANCH: "s3server-pr"/g' ${CORTX_ISO_LOCATION}/RELEASE.INFO
                        sh build_readme.sh "${DESTINATION_RELEASE_LOCATION}"
                    popd

                    cp "${THIRD_PARTY_LOCATION}/THIRD_PARTY_RELEASE.INFO" "${DESTINATION_RELEASE_LOCATION}"
                    cp "${CORTX_ISO_LOCATION}/RELEASE.INFO" "${DESTINATION_RELEASE_LOCATION}"

                    cp "${CORTX_ISO_LOCATION}/RELEASE.INFO" .
                '''	

                archiveArtifacts artifacts: "RELEASE.INFO", onlyIfSuccessful: false, allowEmptyArchive: true	
            }

        }

        stage ("Build CORTX-ALL image") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        def buildCortxAllImage = build job: '/Cortx-Kubernetes/cortx-all-docker-image', wait: true,
                            parameters: [
                                string(name: 'CORTX_RE_URL', value: "https://github.com/Seagate/cortx-re"),
                                string(name: 'CORTX_RE_BRANCH', value: "main"),
                                string(name: 'BUILD', value: "${CORTX_BUILD}"),
                                string(name: 'GITHUB_PUSH', value: "yes"),
                                string(name: 'TAG_LATEST', value: "no"),
                                string(name: 'DOCKER_REGISTRY', value: "cortx-docker.colo.seagate.com"),
                                string(name: 'EMAIL_RECIPIENTS', value: "DEBUG")
                            ]
                        env.cortx_all_image = buildCortxAllImage.buildVariables.image
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build CORTX-ALL image"
                    }
                }
            }
        }

        stage ("Deploy") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    build job: "K8s-1N-deployment", wait: true,
                    parameters: [
                        string(name: 'CORTX_RE_REPO', value: "https://github.com/Seagate/cortx-re/"),
                        string(name: 'CORTX_RE_BRANCH', value: "main"),
                        string(name: 'CORTX_IMAGE', value: "${env.cortx_all_image}"),
                        string(name: 'hosts', value: "${host}")
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
