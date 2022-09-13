#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }

    options {
        timeout(time: 300, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '10'))
        parallelsAlwaysFailFast()
    }

    parameters {  
        string(name: 'MOTR_URL', defaultValue: 'https://github.com/Seagate/cortx-motr', description: 'Branch for Motr.')
        string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch for Motr.')
        string(name: 'CORTX_RGW_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw', description: 'Branch for CORTX-RGW')
        string(name: 'CORTX_RGW_BRANCH', defaultValue: 'main', description: 'Branch for CORTX-RGW')
        string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Branch to be used for Hare build.')
        string(name: 'HARE_BRANCH', defaultValue: 'main', description: 'Branch to be used for Hare build.')
        string(name: 'CORTX_CC_URL', defaultValue: 'https://github.com/Seagate/cortx-cc', description: 'Repository URL for cortx-cc build')
        string(name: 'CORTX_CC_BRANCH', defaultValue: 'main', description: 'Branch for cortx-cc build')
        string(name: 'CUSTOM_CI_BUILD_ID', defaultValue: '0', description: 'Custom CI Build Number')
        string(name: 'CORTX_UTILS_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Utils', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX RE', trim: true)
        string(name: 'CORTX_RE_URL', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'CORTX RE Repository URL', trim: true)
        string(name: 'CORTX_UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'CORTX Utils Repository URL', trim: true)
        string(name: 'THIRD_PARTY_PYTHON_VERSION', defaultValue: 'custom', description: 'Third Party Python Version to use', trim: true)
        string(name: 'THIRD_PARTY_RPM_VERSION', defaultValue: 'custom', description: 'Third Party RPM packages Version to use', trim: true)
        // Add os_version parameter in jenkins configuration

        choice(
            name: 'MOTR_BUILD_MODE',
            choices: ['user-mode', 'kernel'],
            description: 'Build motr rpm using kernel or user-mode.'
            )
        choice(
            name: 'ENABLE_MOTR_DTM',
            choices: ['no', 'yes'],
            description: 'Build motr rpm using dtm mode.'
        )
        choice(
            name: 'BUILD_LATEST_CORTX_RGW',
            choices: ['yes', 'no'],
            description: 'Build cortx-rgw from latest code or use last-successful build.'
        )
        choice(
            name: 'BUILD_LATEST_HARE',
                choices: ['yes', 'no'],
                description: 'Build cortx-Hare from latest code or use last-successful build.'
        )
        choice(
            name: 'BUILD_LATEST_CORTX_CC',
                choices: ['yes', 'no'],
                description: 'Build cortx-cc from latest code or use last-successful build.'
        )
    }    

    environment {
        release_dir = "/mnt/bigstorage/releases/cortx"
        branch = "custom-ci"
        component = "motr"
        release_tag = "custom-build-$CUSTOM_CI_BUILD_ID"
        build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/$release_tag/cortx_iso"
    }

    stages {
    
        stage('Install Prerequisite Packages: Ubuntu') {
            when { expression { params.os_version == 'ubuntu-22.04' } }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'install ubuntu packages', script: '''
                    apt update
                    apt install git wget python3-distutils python3-dev libfabric1 libfabric-bin devscripts equivs -y
                '''
            }
        }

        stage('Checkout') {
            steps {
                step([$class: 'WsCleanup'])
                checkout([$class: 'GitSCM', branches: [[name: "$MOTR_BRANCH"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "$MOTR_URL"]]])
            }
        }
    
    
        stage('Install Dependencies') {
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: '', script: '''
                        if [ "${os_version}" == "rockylinux-8.4" ]; then
                            yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/rockylinux/rockylinux-8.4-2.0.0-latest/
                            yum --nogpgcheck -y --disablerepo="EOS_Rocky_8_OS_x86_64_Rocky_8" install libfabric-1.11.2 libfabric-devel-1.11.2
                            export build_number=${BUILD_ID}
                            kernel_src=$(ls -1rd /lib/modules/*/build | head -n1)
                            cp cortx-motr.spec.in cortx-motr.spec
                            sed -i "/BuildRequires.*kernel*/d" cortx-motr.spec
                            sed -i "/BuildRequires.*%{lustre_devel}/d" cortx-motr.spec
                            sed -i 's/@BUILD_DEPEND_LIBFAB@//g' cortx-motr.spec
                            sed -i 's/@.*@/111/g' cortx-motr.spec
                            yum-builddep -y --nogpgcheck cortx-motr.spec
                        else
                            yes | mk-build-deps --install debian/control
                        fi        
                    '''    
            }
        }

        stage('Build RPM Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                        sh label: '', script: '''
                        rm -rf /root/rpmbuild/RPMS/x86_64/*.rpm
                        ./autogen.sh
                        if [ "${MOTR_BUILD_MODE}" == "kernel" ]; then
                            KERNEL=/lib/modules/$(yum list installed kernel | tail -n1 | awk '{ print $2 }').x86_64/build
                            if [ "${ENABLE_MOTR_DTM}" == "yes" ]; then
                                ./configure --with-linux=$KERNEL --enable-dtm0
                            else
                                ./configure --with-linux=$KERNEL
                            fi
                        else
                            if [ "${ENABLE_MOTR_DTM}" == "yes" ]; then
                                ./configure --with-user-mode-only --enable-dtm0
                            else
                                ./configure --with-user-mode-only
                            fi
                        fi
                        export build_number=${CUSTOM_CI_BUILD_ID}
                        if [ "${os_version}" == "rockylinux-8.4" ]; then
                            make rpms
                        else
                            make deb
                        fi            
                    '''
            }
        }

        stage ('Copy RPMS') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    mkdir -p $build_upload_dir
                    cp /root/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir
                    createrepo -v --update $build_upload_dir
                '''
            }
        }
    
        stage ("Trigger Downstream Jobs") {
            parallel {
                stage ("Build CORTX-RGW") {
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        build job: '/GitHub-custom-ci-builds/generic/cortx-rgw-custom-build/', wait: true,
                        parameters: [
                                    string(name: 'CORTX_RGW_BRANCH', value: "${CORTX_RGW_BRANCH}"),
                                    string(name: 'MOTR_BRANCH', value: "custom-ci"),
                                    string(name: 'CORTX_RGW_URL', value: "${CORTX_RGW_URL}"),
                                    string(name: 'CUSTOM_CI_BUILD_ID', value: "${CUSTOM_CI_BUILD_ID}"),
                                    string(name: 'BUILD_LATEST_CORTX_RGW', value: "${BUILD_LATEST_CORTX_RGW}"),
                                    string(name: 'CORTX_RE_URL', value: "${CORTX_RE_URL}"),
                                    string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}")
                                ]
                    }
                }

                stage ("Build Hare") {
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        build job: '/GitHub-custom-ci-builds/generic/hare-custom-build/', wait: true,
                        parameters: [
                                    string(name: 'HARE_BRANCH', value: "${HARE_BRANCH}"),
                                    string(name: 'MOTR_BRANCH', value: "custom-ci"),
                                    string(name: 'HARE_URL', value: "${HARE_URL}"),
                                    string(name: 'CUSTOM_CI_BUILD_ID', value: "${CUSTOM_CI_BUILD_ID}"),
                                    string(name: 'CORTX_UTILS_BRANCH', value: "${CORTX_UTILS_BRANCH}"),
                                    string(name: 'CORTX_UTILS_URL', value: "${CORTX_UTILS_URL}"),
                                    string(name: 'THIRD_PARTY_PYTHON_VERSION', value: "${THIRD_PARTY_PYTHON_VERSION}"),
                                    string(name: 'BUILD_LATEST_HARE', value: "${BUILD_LATEST_HARE}")   
                            ]
                    }
                }

                stage ("Build CORTX-CC") {
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        script {
                            try {
                                def ccbuild = build job: '/GitHub-custom-ci-builds/generic/cortx-cc-custom-build/', wait: true,
                                parameters: [
                                    string(name: 'CORTX_CC_URL', value: "${CORTX_CC_URL}"),
                                    string(name: 'CORTX_CC_BRANCH', value: "${CORTX_CC_BRANCH}"),
                                    string(name: 'CUSTOM_CI_BUILD_ID', value: "${CUSTOM_CI_BUILD_ID}"),
                                    string(name: 'CORTX_UTILS_BRANCH', value: "${CORTX_UTILS_BRANCH}"),
                                    string(name: 'CORTX_UTILS_URL', value: "${CORTX_UTILS_URL}"),
                                    string(name: 'THIRD_PARTY_PYTHON_VERSION', value: "${THIRD_PARTY_PYTHON_VERSION}"),
                                    string(name: 'THIRD_PARTY_RPM_VERSION', value: "${THIRD_PARTY_RPM_VERSION}"),
                                    string(name: 'BUILD_LATEST_CORTX_CC', value: "${BUILD_LATEST_CORTX_CC}")
                                ]
                            } catch (err) {
                                build_stage = env.STAGE_NAME
                                error "Failed to Build CORTX-CC"
                            }        
                        }        
                    }
                }
            }
        }
    }
}