#!/usr/bin/env groovy
properties([[$class: 'ThrottleJobProperty', categories: [], limitOneJobWithMatchingParams: true, maxConcurrentPerNode: 5, maxConcurrentTotal: 5, paramsToUseForLimit: '', throttleEnabled: true, throttleOption: 'project']])

pipeline {
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }

    environment {
        version = "2.0.0"
        branch = "custom-ci"
        release_dir = "/mnt/bigstorage/releases/cortx"
        integration_dir = "$release_dir/github/integration-custom-ci/$os_version/"
        release_tag = "custom-build-$BUILD_ID"
        passphrase = credentials('rpm-sign-passphrase')
        python_deps = "${THIRD_PARTY_PYTHON_VERSION == 'cortx-2.0' ? "$release_dir/third-party-deps/python-deps/python-packages-2.0.0-latest" : THIRD_PARTY_PYTHON_VERSION == 'cortx-1.0' ?  "$release_dir/third-party-deps/python-packages" : "$release_dir/third-party-deps/python-deps/python-packages-2.0.0-custom"}"
        cortx_os_iso = "/mnt/bigstorage/releases/cortx_builds/custom-os-iso/cortx-2.0.0/cortx-os-2.0.0-7.iso"
        third_party_rpm_dir = "${THIRD_PARTY_RPM_VERSION == 'cortx-2.0' ? "$release_dir/third-party-deps/rockylinux/$os_version-2.0.0-latest" : THIRD_PARTY_RPM_VERSION == 'cortx-2.0-k8' ?  "$release_dir/third-party-deps/rockylinux/$os_version-2.0.0-k8" : "$release_dir/third-party-deps/rockylinux/$os_version-custom"}"
    }

    options {
        timeout(time: 300, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
        parallelsAlwaysFailFast()
        buildDiscarder(logRotator(daysToKeepStr: '15', numToKeepStr: '30'))
    }

    parameters {
        string(name: 'CSM_AGENT_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CSM Agent', trim: true)
        string(name: 'CSM_AGENT_URL', defaultValue: 'https://github.com/Seagate/cortx-manager', description: 'CSM_AGENT Repository URL', trim: true)
        string(name: 'HARE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Hare', trim: true)
        string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Hare Repository URL', trim: true)
        string(name: 'HA_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cortx-HA', trim: true)
        string(name: 'HA_URL', defaultValue: 'https://github.com/Seagate/cortx-ha.git', description: 'Cortx-HA Repository URL', trim: true)
        string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Motr', trim: true)
        string(name: 'MOTR_URL', defaultValue: 'https://github.com/Seagate/cortx-motr.git', description: 'Motr Repository URL', trim: true)
        string(name: 'PRVSNR_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Provisioner', trim: true)
        string(name: 'PRVSNR_URL', defaultValue: 'https://github.com/Seagate/cortx-prvsnr.git', description: 'Provisioner Repository URL', trim: true)
        string(name: 'CORTX_RGW_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX-RGW', trim: true)
        string(name: 'CORTX_RGW_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw', description: 'CORTX-RGW Repository URL', trim: true)
        string(name: 'CORTX_RGW_INTEGRATION_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX-RGW-INTEGRATION', trim: true)
        string(name: 'CORTX_RGW_INTEGRATION_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw-integration', description: 'CORTX-RGW-INTEGRATION Repository URL', trim: true)
        string(name: 'CORTX_UTILS_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Utils', trim: true)
        string(name: 'CORTX_UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'CORTX Utils Repository URL', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX RE', trim: true)
        string(name: 'CORTX_RE_URL', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'CORTX RE Repository URL', trim: true)
        // Add os_version parameter in jenkins configuration

        choice(
            name: 'THIRD_PARTY_RPM_VERSION',
            choices: ['cortx-2.0-k8', 'cortx-2.0', 'custom'],
            description: 'Third Party RPM Version to use.'
        )

        choice(
            name: 'MOTR_BUILD_MODE',
            choices: ['user-mode', 'kernel'],
            description: 'Build motr rpm using kernel or user-mode.'
        )

        choice(
            name: 'THIRD_PARTY_PYTHON_VERSION',
            choices: ['cortx-2.0', 'custom'],
            description: 'Third Party Python Version to use.'
        )


        choice(
            name: 'ISO_GENERATION',
            choices: ['no', 'yes'],
            description: 'Need ISO files'
        )
        
        choice(
            name: 'ENABLE_MOTR_DTM',
                choices: ['no', 'yes'],
                description: 'Build motr rpm using dtm mode.'
        )

        choice(
            name: 'ENABLE_ADDB_PLUGIN',
                choices: ['no', 'yes'],
                description: 'Generates addb plugin as part of cortx-rgw-integration.'
        )

        choice(
            name: 'BUILD_LATEST_CORTX_RGW',
            choices: ['yes', 'no'],
            description: 'Build cortx-rgw from latest code or use last-successful build.'
        )

        choice(
            name: 'BUILD_MANAGEMENT_PATH_COMPONENTS',
            choices: ['yes', 'no'],
            description: '''
            Build cortx-management, cortx-ha and cortx-provisioner from latest code or use last-successful build.<br>
            If you select <strong>no</strong>, below parameter values will get ignored<br>
            <strong>CSM_AGENT_BRANCH, CSM_AGENT_URL, HA_BRANCH, HA_URL, PRVSNR_BRANCH, PRVSNR_URL</strong>
            '''
        )

    }

    stages {

        stage ("Build CORTX Utils") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        def cortx_utils_build = build job: '/GitHub-custom-ci-builds/generic/custom-cortx-py-utils', wait: true,
                        parameters: [
                            string(name: 'CORTX_UTILS_URL', value: "${CORTX_UTILS_URL}"),
                            string(name: 'CORTX_UTILS_BRANCH', value: "${CORTX_UTILS_BRANCH}"),
                            string(name: 'CUSTOM_CI_BUILD_ID', value: "${BUILD_NUMBER}")
                        ]
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build CORTX Utils"
                    }
                }
            }
        }

        stage ("Build Provisioner") {
            when { expression { params.BUILD_MANAGEMENT_PATH_COMPONENTS == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        def prvsnrbuild = build job: '/GitHub-custom-ci-builds/generic/prvsnr-custom-build', wait: true,
                        parameters: [
                            string(name: 'PRVSNR_URL', value: "${PRVSNR_URL}"),
                            string(name: 'PRVSNR_BRANCH', value: "${PRVSNR_BRANCH}"),
                            string(name: 'CUSTOM_CI_BUILD_ID', value: "${BUILD_NUMBER}")
                        ]
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build Provisioner"
                    }
                }
            }
        }

        stage ("Trigger Component Jobs") {
            parallel {
                stage('Install Dependecies') {
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        sh label: 'Installed Dependecies', script: '''
                            yum install -y expect rpm-sign rng-tools genisoimage
                            #systemctl start rngd
                            '''
                    }
                }

                stage ("Build Motr and Hare") {
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        script {
                            try {
                                def motrbuild = build job: '/GitHub-custom-ci-builds/generic/motr-custom-build', wait: true,
                                        parameters: [
                                                        string(name: 'MOTR_URL', value: "${MOTR_URL}"),
                                                        string(name: 'MOTR_BRANCH', value: "${MOTR_BRANCH}"),
                                                        string(name: 'MOTR_BUILD_MODE', value: "${MOTR_BUILD_MODE}"),
                                                        string(name: 'ENABLE_MOTR_DTM', value: "${ENABLE_MOTR_DTM}"),
                                                        string(name: 'CORTX_RGW_URL', value: "${CORTX_RGW_URL}"),
                                                        string(name: 'CORTX_RGW_BRANCH', value: "${CORTX_RGW_BRANCH}"),
                                                        string(name: 'HARE_URL', value: "${HARE_URL}"),
                                                        string(name: 'HARE_BRANCH', value: "${HARE_BRANCH}"),
                                                        string(name: 'CUSTOM_CI_BUILD_ID', value: "${BUILD_NUMBER}"),
                                                        string(name: 'CORTX_UTILS_BRANCH', value: "${CORTX_UTILS_BRANCH}"),
                                                        string(name: 'CORTX_UTILS_URL', value: "${CORTX_UTILS_URL}"),
                                                        string(name: 'THIRD_PARTY_PYTHON_VERSION', value: "${THIRD_PARTY_PYTHON_VERSION}"),
                                                        string(name: 'BUILD_LATEST_CORTX_RGW', value: "${BUILD_LATEST_CORTX_RGW}")
                                                    ]
                            } catch (err) {
                                build_stage = env.STAGE_NAME
                                error "Failed to Build Motr, Hare and S3Server"
                            }
                        }
                    }
                }

                stage ("Build CORTX RGW Integration") {
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        script {
                            try {
                                def rgwintegrationbuild = build job: '/GitHub-custom-ci-builds/generic/cortx-rgw-integration-build', wait: true,
                                          parameters: [
                                              string(name: 'CORTX_RGW_INTEGRATION_URL', value: "${CORTX_RGW_INTEGRATION_URL}"),
                                              string(name: 'CORTX_RGW_INTEGRATION_BRANCH', value: "${CORTX_RGW_INTEGRATION_BRANCH}"),
                                              string(name: 'CUSTOM_CI_BUILD_ID', value: "${BUILD_NUMBER}"),
                                              string(name: 'MOTR_URL', value: "${MOTR_URL}"),
                                              string(name: 'MOTR_BRANCH', value: "${MOTR_BRANCH}"),
                                              string(name: 'CORTX_RGW_URL', value: "${CORTX_RGW_URL}"),
                                              string(name: 'CORTX_RGW_BRANCH', value: "${CORTX_RGW_BRANCH}"),                                             
                                              string(name: 'ENABLE_ADDB_PLUGIN', value: "${ENABLE_ADDB_PLUGIN}")
                                          ]
                            } catch (err) {
                                build_stage = env.STAGE_NAME
                                error "Failed to Build RGW Integration"
                            }
                        }
                    }
                }

                stage ("Build HA") {
                    when { expression { params.BUILD_MANAGEMENT_PATH_COMPONENTS == 'yes' } }
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        script {
                            try {
                                def habuild = build job: '/GitHub-custom-ci-builds/generic/cortx-ha-custom-build', wait: true,
                                          parameters: [
                                              string(name: 'HA_URL', value: "${HA_URL}"),
                                              string(name: 'HA_BRANCH', value: "${HA_BRANCH}"),
                                              string(name: 'CUSTOM_CI_BUILD_ID', value: "${BUILD_NUMBER}"),
                                              string(name: 'CORTX_UTILS_BRANCH', value: "${CORTX_UTILS_BRANCH}"),
                                              string(name: 'CORTX_UTILS_URL', value: "${CORTX_UTILS_URL}"),
                                              string(name: 'THIRD_PARTY_PYTHON_VERSION', value: "${THIRD_PARTY_PYTHON_VERSION}")
                                          ]
                            } catch (err) {
                                build_stage = env.STAGE_NAME
                                error "Failed to Build HA"
                            }
                        }
                    }
                }

                stage ("Build CSM Agent") {
                    when { expression { params.BUILD_MANAGEMENT_PATH_COMPONENTS == 'yes' } }
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        script {
                            try {
                                def csm_agent_build = build job: '/GitHub-custom-ci-builds/generic/custom-csm-agent-build', wait: true,
                                              parameters: [
                                                    string(name: 'CSM_AGENT_URL', value: "${CSM_AGENT_URL}"),
                                                    string(name: 'CSM_AGENT_BRANCH', value: "${CSM_AGENT_BRANCH}"),
                                                    string(name: 'CUSTOM_CI_BUILD_ID', value: "${BUILD_NUMBER}"),
                                                    string(name: 'CORTX_UTILS_BRANCH', value: "${CORTX_UTILS_BRANCH}"),
                                                    string(name: 'CORTX_UTILS_URL', value: "${CORTX_UTILS_URL}"),
                                                    string(name: 'THIRD_PARTY_PYTHON_VERSION', value: "${THIRD_PARTY_PYTHON_VERSION}")
                                              ]
                            } catch (err) {
                                build_stage = env.STAGE_NAME
                                error "Failed to Build CSM Agent"
                            }
                        }
                    }
                }
            }
        }


        stage ('Collect Component RPMS') {
            steps {
                script { build_stage = env.STAGE_NAME }

                checkout([$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])

                sh label: 'Copy RPMS', script:'''
                    RPM_COPY_PATH="/mnt/bigstorage/releases/cortx/components/github/main/$os_version/dev/"

                    if [ "$BUILD_MANAGEMENT_PATH_COMPONENTS" == "yes" ]; then
                        CUSTOM_COMPONENT_NAME="motr|hare|cortx-ha|provisioner|csm-agent|cortx-utils|cortx-rgw|cortx-rgw-integration"
                    else
                        CUSTOM_COMPONENT_NAME="motr|hare|cortx-rgw|cortx-rgw-integration"    
                    fi

                    pushd $RPM_COPY_PATH
                    for component in `ls -1 | grep -E -v "$CUSTOM_COMPONENT_NAME" | grep -E -v 'luster|halon|mero|motr|cortx-extension|nfs|cortx-utils|cortx-prereq'`
                    do
                        echo -e "Copying RPM's for $component"
                        if ls $component/last_successful/*.rpm 1> /dev/null 2>&1; then
                            cp $component/last_successful/*.rpm $integration_dir/$release_tag/cortx_iso/
                        else
                            echo "Packages not available for $component. Exiting"
                        exit 1
                        fi
                    done

                    createrepo -v --update $integration_dir/$release_tag/cortx_iso/

                '''
            }
        }

        stage('RPM Validation') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Validate RPMS for Motr Dependency', script:'''
                for env in "dev" ;
                do
                    set +x
                    echo "VALIDATING $env RPM'S................"
                    echo "-------------------------------------"
                    pushd $integration_dir/$release_tag/cortx_iso/
                    if [ "${CSM_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${HARE_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${MOTR_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${PRVSNR_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${S3_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${SSPL_BRANCH}" == "Cortx-v1.0.0_Beta" ]; then
                            mero_rpm=$(ls -1 | grep "eos-core" | grep -E -v "eos-core-debuginfo|eos-core-devel|eos-core-tests")
                        else
                            mero_rpm=$(ls -1 | grep "cortx-motr" | grep -E -v "cortx-motr-debuginfo|cortx-motr-devel|cortx-motr-tests|cortx-motr-ivt|cortx-motr-debugsource")
                        fi
                    mero_rpm_release=`rpm -qp ${mero_rpm} --qf '%{RELEASE}' | tr -d '\040\011\012\015'`
                    mero_rpm_version=`rpm -qp ${mero_rpm} --qf '%{VERSION}' | tr -d '\040\011\012\015'`
                    mero_rpm_release_version="${mero_rpm_version}-${mero_rpm_release}"
                    for component in `ls -1`
                    do
                        if [ "${CSM_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${HARE_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${MOTR_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${PRVSNR_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${S3_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${SSPL_BRANCH}" == "Cortx-v1.0.0_Beta" ]; then
                             mero_dep=`echo $(rpm -qpR ${component} | grep -E "eos-core = |mero =") | cut -d= -f2 | tr -d '\040\011\012\015'`
                        else
                            mero_dep=`echo $(rpm -qpR ${component} | grep -E "cortx-motr = |mero =") | cut -d= -f2 | tr -d '\040\011\012\015'`
                        fi
                        if [ -z "$mero_dep" ]
                        then
                            echo "\033[1;33m $component has no dependency to Motr - Validation Success \033[0m "
                        else
                            if [ "$mero_dep" = "$mero_rpm_release_version" ]; then
                                echo "\033[1;32m $component Motr version matches with integration mero rpm($mero_rpm_release_version) Good to Go - Validation Success \033[0m "
                            else
                                echo "\033[1;31m $component Motr version mismatchs with integration mero rpm($mero_rpm_release_version) - Validation Failed \033[0m"
                                exit 1
                            fi
                        fi
                    done
                done
                '''
            }
        }

        stage ('Sign rpm') {
            when { expression { false } }
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Generate Key', script: '''
                    set +x
                    pushd scripts/rpm-signing
                    cat gpgoptions >>  ~/.rpmmacros
                    sed -i 's/passphrase/'${passphrase}'/g' genkey-batch
                    gpg --batch --gen-key genkey-batch
                    gpg --export -a 'Seagate'  > RPM-GPG-KEY-Seagate
                    rpm --import RPM-GPG-KEY-Seagate
                    popd
                '''
                sh label: 'Sign RPM', script: '''
                    set +x
                    pushd scripts/rpm-signing
                    chmod +x rpm-sign.sh
                    cp RPM-GPG-KEY-Seagate $integration_dir/$release_tag/cortx_iso/

                    for rpm in `ls -1 $integration_dir/$release_tag/cortx_iso/*.rpm`
                    do
                    ./rpm-sign.sh ${passphrase} $rpm
                    done
                    popd

                '''
            }
        }

        stage ('Repo Creation') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Repo Creation', script: '''
                    pushd $integration_dir/$release_tag/cortx_iso/
                    rpm -qi createrepo || yum install -y createrepo
                    createrepo -v --update .
                    popd
                '''
            }
        }

        stage ('Link 3rd_party and python_deps') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Tag Release', script: '''
                    pushd $integration_dir/$release_tag
                        ln -s $(readlink -f $third_party_rpm_dir) 3rd_party
                        ln -s $(readlink -f $python_deps) python_deps
                    popd
                '''
            }
        }

        stage ('Build MANIFEST') {
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Build MANIFEST', script: """
                    pushd scripts/release_support
                        sh build_release_info.sh -b $branch -v $version -l $integration_dir/$release_tag/cortx_iso/ -t $integration_dir/$release_tag/3rd_party
                        sh build_readme.sh $integration_dir/$release_tag
                    popd

                    cp $integration_dir/$release_tag/README.txt .
                    cp $integration_dir/$release_tag/cortx_iso/RELEASE.INFO .
                    cp $integration_dir/$release_tag/3rd_party/THIRD_PARTY_RELEASE.INFO $integration_dir/$release_tag
                    cp $integration_dir/$release_tag/cortx_iso/RELEASE.INFO $integration_dir/$release_tag
                """
            }
        }

        stage ('Generate ISO Image') {
            when {
                expression { params.ISO_GENERATION == 'yes' }
            }
            steps {

                sh label: 'Release ISO', script: '''
                mkdir -p $integration_dir/$release_tag/iso && pushd $integration_dir/$release_tag/iso
                    genisoimage -input-charset iso8859-1 -f -J -joliet-long -r -allow-lowercase -allow-multidot -hide-rr-moved -publisher Seagate -o $integration_dir/$release_tag/iso/cortx-$version-$release_tag-single.iso $integration_dir/$release_tag
                popd
                '''

                sh label: 'Upgrade ISO', script: '''
                #Create upgrade directorty structure
                mkdir -p $integration_dir/$release_tag/sw_upgrade/{3rd_party,cortx_iso,python_deps}
                createrepo -v $integration_dir/$release_tag/sw_upgrade/3rd_party/
                find $integration_dir/$release_tag/3rd_party/ -not -path '*repodata*' -type d  -printf '%P\n' | xargs -t -I % sh -c '{ mkdir -p $integration_dir/$release_tag/sw_upgrade/3rd_party/%; createrepo -q $integration_dir/$release_tag/sw_upgrade/3rd_party/%; }'

                #Copy all component packages
                cp -r $integration_dir/$release_tag/cortx_iso/* $integration_dir/$release_tag/sw_upgrade/cortx_iso/

                #Copy RELEASE.INFO, Third Party RPM and Python index files.
                cp $integration_dir/$release_tag/3rd_party/THIRD_PARTY_RELEASE.INFO $integration_dir/$release_tag/sw_upgrade/3rd_party
                sed -i -e /tar/d -e /rpm/d -e /tgz/d $integration_dir/$release_tag/sw_upgrade/3rd_party/THIRD_PARTY_RELEASE.INFO
                cp $integration_dir/$release_tag/python_deps/index.html $integration_dir/$release_tag/sw_upgrade/python_deps/index.html
                sed -i /href/d $integration_dir/$release_tag/sw_upgrade/python_deps/index.html
                cp $integration_dir/$release_tag/cortx_iso/RELEASE.INFO $integration_dir/$release_tag/sw_upgrade/

                genisoimage -input-charset iso8859-1 -f -J -joliet-long -r -allow-lowercase -allow-multidot -hide-rr-moved -publisher Seagate -o $integration_dir/$release_tag/iso/cortx-$version-$release_tag-upgrade.iso $integration_dir/$release_tag/sw_upgrade
                rm -rf $integration_dir/$release_tag/sw_upgrade

                '''

                sh label: "Sign ISO files", script: '''
                pushd scripts/rpm-signing
                    gpg --output $integration_dir/$release_tag/iso/cortx-$version-$release_tag-upgrade.iso.sig --detach-sig $integration_dir/$release_tag/iso/cortx-$version-$release_tag-upgrade.iso
                    sleep 5
                    gpg --output $integration_dir/$release_tag/iso/cortx-$version-$release_tag-single.iso.sig --detach-sig $integration_dir/$release_tag/iso/cortx-$version-$release_tag-single.iso
                popd
                '''
            }
        }

        stage ('Cleanup') {
            when { expression { false } }
            steps {

                sh label: 'Cleanup', script:'''
                #Remove Build details from THIRD_PARTY_RELEASE.INFO
                sed -i '/BUILD/d' $integration_dir/$release_tag/3rd_party/THIRD_PARTY_RELEASE.INFO
                '''

            }
        }
        
       stage ("Build CORTX images") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        def build_cortx_all_image = build job: 'GitHub-custom-ci-builds/generic/cortx-all-docker-image/', wait: true,
                                    parameters: [
                                        string(name: 'CORTX_RE_URL', value: "${CORTX_RE_URL}"),
                                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                                        string(name: 'BUILD', value: "${release_tag}"),
                                        string(name: 'GITHUB_PUSH', value: "yes"),
                                        string(name: 'TAG_LATEST', value: "no"),
                                        string(name: 'DOCKER_REGISTRY', value: "cortx-docker.colo.seagate.com"),
                                        string(name: 'OS', value: "${os_version}"),
                                        string(name: 'EMAIL_RECIPIENTS', value: "DEBUG")
                                        ]
                    env.cortx_all_image = build_cortx_all_image.buildVariables.image
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build CORTX images"
                    }
                }
            }
       } 

        stage ('Print Build Information') {
            steps {
                script { build_stage = env.STAGE_NAME }

            sh label: 'Print Release Build and ISO location', script:'''
                echo "Custom Release Build is available at,"
                echo "http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/$os_version/$release_tag/"
                echo "CORTX images are available at,"
                echo "${cortx_all_image}"
                '''
            }
        }
    }

    post {

        success {
                sh label: 'Delete Old Builds', script: '''
                set +x
                find ${integration_dir}/* -maxdepth 0 -mtime +5 -type d -exec rm -rf {} \\;
                '''
        }

        always {
            script {
                env.release_build_location = "http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/${env.os_version}/${env.release_tag}"
                env.release_build = "${env.release_tag}"
                env.build_stage = "${build_stage}"
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]

                def toEmail = ""
                if ( manager.build.result.toString() == "FAILURE") {
                    toEmail = "CORTX.DevOps.RE@seagate.com"
                }
                
                emailext (
                    body: '''${SCRIPT, template="K8s-release-email.template"}''',
                    mimeType: 'text/html',
                    subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                    attachLog: true,
                    to: toEmail,
                    recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}