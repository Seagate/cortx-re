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
        ansiColor('xterm')  
    }


    parameters {  
	    string(name: 'CORTX_RE_URL', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repo for cortx-re')
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch for cortx-re')
        string(name: 'PY_UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'Repo for Py-Utils Agent')
        string(name: 'PY_UTILS_BRANCH', defaultValue: 'main', description: 'Branch for Py-Utils Agent')     
        // string(name: 'HOST', defaultValue: '-', description: 'Host=FQDN,user=USERNAME,pass=PASSWORD',  trim: true)
        string(name: 'COMPONENTS_BRANCH', defaultValue: 'main', description: 'Branch for other components',  trim: true)
        choice(
            name: 'OS_VERSION',
            choices: ['centos-7.9.2009', 'centos-7.8.2003'],
            description: 'OS version of HOST'
        )
        choice(
            name: 'DEPLOY_BUILD_ON_NODES',
            choices: ["Both", "1node", "3node" ],
            description: '''<pre>If you select Both then build will be deploy on 1 node as well as 3 node. If you select 1 node then build will be deploy on 1 node only. 
                            If you select 3 node then build will be deploy on 3 node only.</pre>'''
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
        // OS_VERSION, singlenode_host, threenode_hosts and COMPONENTS_BRANCH are manually created parameters in jenkins job.

        COMPONENT_NAME = "cortx-utils".trim()
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
		
        build_id = sh(script: "echo ${CORTX_BUILD} | rev | cut -d '/' -f2,3 | rev", returnStdout: true).trim()
        STAGE_DEPLOY = "yes"
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
                        cd $PWD/py-utils/test
                        python3 setup.py bdist_rpm --requires cortx-py-utils --version=1.0.0 --post-install test-post-install --post-uninstall test-post-uninstall
                    '''
                }
            }
        }

        //  Release cortx deployment stack
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
                    systemctl start rngd
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
                    cp ./cortx_utils/py-utils/test/dist/!(*.src.rpm|*.tar.gz) "${CORTX_ISO_LOCATION}"
                    cp ./cortx_utils/py-utils/dist/!(*.src.rpm|*.tar.gz) "${CORTX_ISO_LOCATION}"
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
                    echo -e "Creating release information files"
                    pushd cortx-re/scripts/release_support
                        sh build_release_info.sh -v ${VERSION} -l ${CORTX_ISO_LOCATION} -t ${THIRD_PARTY_LOCATION}
                        sh build_readme.sh "${DESTINATION_RELEASE_LOCATION}"
                        sed -i -e 's/BRANCH:.*/BRANCH: "PyUtils-pr"/g' ${CORTX_ISO_LOCATION}/RELEASE.INFO
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
                                string(name: 'CORTX_RE_URL', value: "${CORTX_RE_URL}"),
                                string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                                string(name: 'BUILD', value: "${CORTX_BUILD}"),
                                string(name: 'GITHUB_PUSH', value: "yes"),
                                string(name: 'PUSH_TEST_RPMS', value: "yes"),
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

        stage ('Deploy Cortx Cluster') {
            parallel {
                stage ("Deploy 1Node") {
                    when { expression { params.DEPLOY_BUILD_ON_NODES ==~ /Both|1node/ } }
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        script {
                            build job: "/Cortx-PR-Build/Cortx-Deployment/Kubernetes/K8s-1N-deployment", wait: true,
                            parameters: [
                                string(name: 'CORTX_RE_REPO', value: "https://github.com/Seagate/cortx-re/"),
                                string(name: 'CORTX_RE_BRANCH', value: "main"),
                                string(name: 'CORTX_IMAGE', value: "${env.cortx_all_image}"),
                                string(name: 'hosts', value: "${singlenode_host}")
                            ] 
                        }
                    }
                }
         	    stage ("Deploy 3Node") {
                    when { expression { params.DEPLOY_BUILD_ON_NODES ==~ /Both|3node/ } }
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        script {
                            build job: '/Cortx-PR-Build/Cortx-Deployment/Kubernetes/K8s-3N-deployment', wait: true,
                            parameters: [
                                string(name: 'CORTX_RE_BRANCH', value: "main"),
                                string(name: 'CORTX_RE_REPO', value: "https://github.com/Seagate/cortx-re/"),
                                string(name: 'CORTX_IMAGE', value: "${env.cortx_all_image}"),
                                text(name: 'hosts', value: "${threenode_hosts}"),
                            ]
                        }
                    }
                }
            }
        }

        stage ("Test RPM") {
            steps {
                script {
                    sh label: 'RUN command to test rpm', script: '''
                        if [ $DEPLOY_BUILD_ON_NODES == '1node' ]; then
                            echo $singlenode_host | sed 's/,/ /g' | sed 's/=/ /g' |  awk -F ' ' '{ print $2" "$4" "$6 }' > server_info.txt
                            NODE_HOST=$( cat server_info.txt | awk -F ' ' '{ print $1 }')
                            NODE_USER=$( cat server_info.txt | awk -F ' ' '{ print $2 }')
                            NODE_PASS=$( cat server_info.txt | awk -F ' ' '{ print $3 }')
                        else
                            echo $threenode_hosts | head -n1 | sed 's/,/ /g' | sed 's/=/ /g' |  awk -F ' ' '{ print $2" "$4" "$6 }' > server_info.txt
                            NODE_HOST=$( cat server_info.txt | awk -F ' ' '{ print $1 }')
                            NODE_USER=$( cat server_info.txt | awk -F ' ' '{ print $2 }')
                            NODE_PASS=$( cat server_info.txt | awk -F ' ' '{ print $3 }')
                        fi
                        yum install sshpass -y
                        sshpass -p ${NODE_PASS} ssh -o StrictHostKeyChecking=no ${NODE_USER}@${NODE_HOST} kubectl exec '$(kubectl get pods | grep "cortx-data" | awk '{print$1}')' --container cortx-hax --  yum install http://cortx-storage.colo.seagate.com/releases/cortx/github/pr-build/${BRANCH}/${COMPONENT_NAME}/${BUILD_NUMBER}/cortx_iso/cortx-py-utils-test-1.0.0-1.noarch.rpm -y
                        sshpass -p ${NODE_PASS} ssh -o StrictHostKeyChecking=no ${NODE_USER}@${NODE_HOST} kubectl exec '$(kubectl get pods | grep "cortx-data" | awk '{print$1}')' --container cortx-hax -- /opt/seagate/cortx/utils/bin/utils_setup test --config yaml:///etc/cortx/cluster.conf --plan sanity
                    '''
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