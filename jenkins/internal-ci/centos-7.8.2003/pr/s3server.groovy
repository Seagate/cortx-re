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

    environment{

        REPO_URL = "https://github.com/gowthamchinna/cortx-s3server.git"
        COMPONENT_NAME = "s3server".trim()
        BRANCH = "main"
        OS_VERSION = "centos-7.8.2003"
        THIRD_PARTY_VERSION = "1.0.0-3"
        VERSION = "2.0.0"
        PASSPHARASE = credentials('rpm-sign-passphrase')

        PULL_ID = "${ghprbPullLink}".substring("${ghprbPullLink}".lastIndexOf('/') + 1).trim()
        PR_IDENTIFIER = "PR-${PULL_ID != null ? PULL_ID : 'TEMP'}"

        DESTINATION_RELEASE_LOCATION="/mnt/bigstorage/releases/cortx/github/pr-build/${COMPONENT_NAME}/${PR_IDENTIFIER}"
        PYTHON_DEPS="/mnt/bigstorage/releases/cortx/third-party-deps/python-packages"
        THIRD_PARTY_DEPS="/mnt/bigstorage/releases/cortx/third-party-deps/centos/centos-7.8.2003-${THIRD_PARTY_VERSION}/"
        COMPONENTS_RPM="/mnt/bigstorage/releases/cortx/components/github/${BRANCH}/${OS_VERSION}/dev/"
        DESTNATION_HTTP_URL="http://cortx-storage.colo.seagate.com/releases/cortx/github/pr-build/${COMPONENT_NAME}/${PR_IDENTIFIER}"

        CORTX_ISO_LOCATION="${DESTINATION_RELEASE_LOCATION}/cortx_iso"
        THIRD_PARTY_LOCATION="${DESTINATION_RELEASE_LOCATION}/3rd_party"
        PYTHON_LIB_LOCATION="${DESTINATION_RELEASE_LOCATION}/python_deps"
    }

    stages {

        stage('Checkout') {
            steps {
				script { build_stage=env.STAGE_NAME }

                script {
                    
                    manager.addHtmlBadge("&emsp;<b>Build :</b> <a href=\"${DESTNATION_HTTP_URL}\"><b>${PR_IDENTIFIER}</b></a> <br />")

                    dir("cortx-s3server"){
                        checkout([$class: 'GitSCM', branches: [[name: "${ghprbActualCommit}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${REPO_URL}",  name: 'origin', refspec: "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"]]])
                    }

                    dir("cortx-re"){
                        checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
                    }
                
                }
            }
        }

        stage('Build') {
            steps {
				script { build_stage=env.STAGE_NAME }
                 
                dir("cortx-s3server"){
                    sh label: 'prepare build env', script: """
                        sed '/baseurl/d' /etc/yum.repos.d/motr_current_build.repo
                        echo "baseurl=http://cortx-storage.colo.seagate.com/releases/cortx/components/github/${BRANCH}/${OS_VERSION}/dev/motr/current_build/"  >> /etc/yum.repos.d/motr_current_build.repo
                        yum clean all;rm -rf /var/cache/yum
                    """

                    sh label: 'Build s3server RPM', script: '''
                        yum clean all;rm -rf /var/cache/yum
                        export build_number=${BUILD_ID}
                        yum install cortx-motr{,-devel} -y
                        yum erase log4cxx_eos-devel -q -y
                        ./rpms/s3/buildrpm.sh -S $VERSION -P $PWD -l
                        
                    '''
                    sh label: 'Build s3iamcli RPM', script: '''
                        export build_number=${BUILD_ID}
                        ./rpms/s3iamcli/buildrpm.sh -S $VERSION -P $PWD
                    '''
                }
            }
        }

        stage('Release') {
            steps {
				script { build_stage=env.STAGE_NAME }

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
	}

	post {
		always {
			script{
                /* clean up our workspace */
				deleteDir() 

                //add_pr_comment("All Good")
			}
		}
    }
}	

// def add_pr_comment(def text_pr) {
//     def repository_name = "gowthamchinna/cortx-prvsnr"
//     withCredentials([usernamePassword(credentialsId: "cortx-admin-github", passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'USER')]) {
//         sh "curl -s -H \"Authorization: token ${GITHUB_TOKEN}\" -X POST -d '{\"body\": \"${text_pr}\"}' \"https://api.github.com/repos/${repository_name}/issues/${ghprbPullId}/comments\""
//     }
// }