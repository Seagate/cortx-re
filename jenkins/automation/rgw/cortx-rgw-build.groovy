#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label 'ceph-build-node'
        }
    }

    triggers { cron('30 22 * * *') }

    environment {
        branch = "custom-ci"
        os_version = "rockylinux-8.4"
        version = "2.0.0"
        component = "rgw-build/"
        release_dir = "/mnt/bigstorage/releases/cortx/"
        python_deps = "$release_dir/third-party-deps/python-deps/python-packages-2.0.0-latest"
        third_party_rpm_dir = "$release_dir/third-party-deps/rockylinux/$os_version-2.0.0-latest"
        integration_dir = "$release_dir/$component/release"
        release_tag = "cortx-rgw-build-$BUILD_ID"
    }

    parameters {
        string(name: 'MOTR_URL', defaultValue: 'https://github.com/Seagate/cortx-motr', description: 'Repository URL for Motr build')
        string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch for Motr build')
        string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Repository URL for Hare build')
        string(name: 'HARE_BRANCH', defaultValue: 'main', description: 'Branch for Hare build')
        string(name: 'UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'Repository URL for Utils build')
        string(name: 'UTILS_BRANCH', defaultValue: 'main', description: 'Branch for Utils build')
        string(name: 'PRVSNR_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Provisioner', trim: true)
        string(name: 'PRVSNR_URL', defaultValue: 'https://github.com/Seagate/cortx-prvsnr.git', description: 'Provisioner Repository URL', trim: true)
        string(name: 'CORTX_RGW_INTEGRATION_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX RGW integration', trim: true)
        string(name: 'CORTX_RGW_INTEGRATION_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw-integration', description: 'CORTX RGW integration Repository URL', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'rocky-linux-8.4', description: 'Branch or GitHash for CORTX RE', trim: true)
        string(name: 'CORTX_RE_URL', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'CORTX RE Repository URL', trim: true)
        string(name: 'CEPH_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw', description: 'Repository URL for ceph build')
        string(name: 'CEPH_BRANCH', defaultValue: 'main', description: 'Branch for ceph build')

        choice(
            name: 'BUILD_LATEST_CORTX_RGW',
            choices: ['yes', 'no'],
            description: 'Build cortx-rgw from latest code or use last-successful build.'
        )

    }
    
    options {
        timestamps() 
    }

    stages {
    
        stage('Checkout Component Codebase') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('ceph') {
                    checkout([$class: 'GitSCM', branches: [[name: "${CEPH_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CEPH_URL}"]]])
                }

                dir ('motr') {
                    checkout([$class: 'GitSCM', branches: [[name: "$MOTR_BRANCH"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "$MOTR_URL"]]])
                }   

                dir ('cortx-py-utils') {
                    checkout([$class: 'GitSCM', branches: [[name: "${UTILS_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "$UTILS_URL"]]])
                }

                dir ('hare') {
                    checkout([$class: 'GitSCM', branches: [[name: "${HARE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', noTags: true, reference: '', shallow: true], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', shallow: true, trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', refspec: '+refs/heads/main:refs/remotes/origin/main', url: "$HARE_URL"]]])
                }

                dir ('provisioner') {
                    checkout([$class: 'GitSCM', branches: [[name: "${PRVSNR_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${PRVSNR_URL}"]]])
                }

                dir ('cortx-rgw-integration') {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RGW_INTEGRATION_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RGW_INTEGRATION_URL}"]]])
                } 

            }
        }
        
        stage ('Prepare') {
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Clean up', script: '''
                rm -rf /root/rpmbuild/RPMS/x86_64/*.rpm
                rm -rf $release_dir/$component/$branch/rpmbuild/SOURCES/ceph*.tar.bz2
                rm -rf  $release_dir/$component/$branch/rpmbuild/BUILD/ceph*
                yum erase cortx-py-utils cortx-motr{,-devel} -y
                rm -f /etc/yum.repos.d/cortx-storage.colo.seagate.com* /etc/yum.repos.d/root_rpmbuild_RPMS_x86_64.repo
                '''

                sh label: 'prepare', script: '''
                yum install createrepo systemd-devel libuuid libuuid-devel libaio-devel openssl openssl-devel perl-File-Slurp gcc cmake3 cmake rpm-build rpmdevtools autoconf automake libtool gcc-c++ -y 
                rpmdev-setuptree
                mkdir -p /root/rpmbuild/RPMS/x86_64/
                yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/rockylinux/rockylinux-8.4-2.0.0-latest/
                '''
            }
        }

        stage ('Build CORTX RGW Integration Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build CORTX RGW Integration', script: '''
                pushd cortx-rgw-integration
                    bash ./jenkins/build.sh -v $version -b ${BUILD_ID}
                    shopt -s extglob
                    if ls ./dist/*.rpm; then
                        cp ./dist/!(*.src.rpm|*.tar.gz) /root/rpmbuild/RPMS/x86_64/
                    fi
                popd
            '''
            }
        }

        stage ('Build CORTX Utils Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build and Install', script: '''
                pushd cortx-py-utils
                    ./jenkins/build.sh -v $version -b $BUILD_NUMBER
                popd
                pip3 install --no-cache-dir --trusted-host cortx-storage.colo.seagate.com -i http://cortx-storage.colo.seagate.com/releases/cortx/github/main/centos-7.9.2009/last_successful_prod/python_deps/ -r https://raw.githubusercontent.com/Seagate/cortx-utils/main/py-utils/python_requirements.txt -r https://raw.githubusercontent.com/Seagate/cortx-utils/main/py-utils/python_requirements.ext.txt
                yum localinstall -y ./cortx-py-utils/py-utils/dist/cortx-py-utils-2.0.0*.noarch.rpm
                mv ./cortx-py-utils/py-utils/dist/cortx-py-utils-2.0.0*.noarch.rpm /root/rpmbuild/RPMS/x86_64/
                
                '''
            }
        }

        stage ('Build Motr Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Install Motr', script: '''
                pushd motr
                        yum --nogpgcheck -y --disablerepo="EOS_Rocky_8_OS_x86_64_Rocky_8" install libfabric-1.11.2 libfabric-devel-1.11.2
                        yum install perl-YAML-LibYAML perl-List-MoreUtils perl-XML-LibXML castxml perl-File-Find-Rule perl-IO-All asciidoc libedit-devel python2-devel -y
                        cp cortx-motr.spec.in cortx-motr.spec
                        sed -i "/BuildRequires.*kernel*/d" cortx-motr.spec
                        sed -i "/BuildRequires.*%{lustre_devel}/d" cortx-motr.spec
                        sed -i 's/@BUILD_DEPEND_LIBFAB@//g' cortx-motr.spec
                        sed -i 's/@.*@/111/g' cortx-motr.spec
                        yum-builddep -y --nogpgcheck cortx-motr.spec
                        ./autogen.sh && ./configure --with-user-mode-only
                        export build_number=${BUILD_ID}
                        make rpms
                        #rm -rf /root/rpmbuild/RPMS/x86_64/*debug*.rpm
                        createrepo -v  /root/rpmbuild/RPMS/x86_64/ && yum-config-manager --add-repo /root/rpmbuild/RPMS/x86_64/
                        yum install cortx-motr{,-devel} --nogpgcheck -y
                        rm -rf /etc/yum.repos.d/cortx-storage.colo.seagate.com*
                popd
                '''
            }
        }

        stage ('Build Hare Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build Hare', script: '''
                pushd hare
                    export build_number=${BUILD_ID}
                    make VERSION=$version rpm
                popd
            '''
            }
        }
    
        stage ('Build Provisioner Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build Provisioner', script: '''
                pushd provisioner
                    if [ -f "./jenkins/build.sh" ]; then
                        bash ./jenkins/build.sh -v $version -b ${BUILD_ID}
                    else
                        echo "cortx-provisioner package creation is not implemented"
                    fi
                    shopt -s extglob
                    if ls ./dist/*.rpm; then
                        cp ./dist/!(*.src.rpm|*.tar.gz) /root/rpmbuild/RPMS/x86_64/
                    fi
                popd
            '''
            }
        }

        stage('Build Ceph Package') {
            when { expression { params.BUILD_LATEST_CORTX_RGW == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build', script: '''

                pushd ceph
                    sed -i 's/centos|/rocky|centos|/' install-deps.sh
                    ./install-deps.sh
                    ./make-dist
                    mkdir -p $release_dir/$component/$branch/rpmbuild/$BUILD_NUMBER/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
                    mv ceph*.tar.bz2 $release_dir/$component/$branch/rpmbuild/$BUILD_NUMBER/SOURCES/
                    tar --strip-components=1 -C $release_dir/$component/$branch/rpmbuild/$BUILD_NUMBER/SPECS/ --no-anchored -xvjf $release_dir/$component/$branch/rpmbuild/$BUILD_NUMBER/SOURCES/ceph*.tar.bz2 "ceph.spec"
                popd

                pushd $release_dir/$component/$branch/rpmbuild/$BUILD_NUMBER
                     rpmbuild --clean --rmsource --define "_unpackaged_files_terminate_build 0" --define "debug_package %{nil}" --without cmake_verbose_logging --without jaeger --without lttng --without seastar --without kafka_endpoint --without zbd --without cephfs_java --without cephfs_shell --without ocf --without selinux --without ceph_test_package --without make_check --define "_binary_payload w2T16.xzdio" --define "_topdir `pwd`" -ba ./SPECS/ceph.spec
                popd

                rm -f $release_dir/$component/$branch/rpmbuild/last_successful && ln -s $release_dir/$component/$branch/rpmbuild/$BUILD_NUMBER $release_dir/$component/$branch/rpmbuild/last_successful

            '''
            }
        }    

        stage ('Copy RPMS') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                pushd $integration_dir
                    rm -rf $release_tag && mkdir -p $release_tag/cortx_iso
                    cp $release_dir/$component/$branch/rpmbuild/last_successful/RPMS/*/*.rpm $integration_dir/$release_tag/cortx_iso
                    mv /root/rpmbuild/RPMS/x86_64/*.rpm $integration_dir/$release_tag/cortx_iso
                    createrepo -v $release_tag/cortx_iso
                    rm -f last_successful && ln -s $release_tag last_successful
                popd    
                '''
            }
        }

        stage ('Release') {
            steps {
                script { build_stage = env.STAGE_NAME }
                checkout([$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])

                sh label: 'Link Third-Party', script: '''
                    pushd $integration_dir/$release_tag
                        ln -s $(readlink -f $third_party_rpm_dir) 3rd_party
                        ln -s $(readlink -f $python_deps) python_deps
                    popd
                '''
                sh label: 'Build MANIFEST', script: """
                    pushd scripts/release_support
                        sh build_release_info.sh -b $branch -v $version -l $integration_dir/$release_tag/cortx_iso -t $integration_dir/$release_tag/3rd_party
                    popd

                    cp $integration_dir/$release_tag/cortx_iso/RELEASE.INFO .
                    cp $integration_dir/$release_tag/3rd_party/THIRD_PARTY_RELEASE.INFO $integration_dir/$release_tag/
                    cp $integration_dir/$release_tag/cortx_iso/RELEASE.INFO $integration_dir/$release_tag/

                """
            }
        }

        stage ("Build CORTX-ALL image") {
                steps {
                    script { build_stage = env.STAGE_NAME }
                    script {
                        try {
                            def build_cortx_rgw_image = build job: '/GitHub-custom-ci-builds/generic/cortx-all-docker-image', wait: true,
                                        parameters: [
                                            string(name: 'CORTX_RE_URL', value: "${CORTX_RE_URL}"),
                                            string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                                            string(name: 'BUILD', value: "http://cortx-storage.colo.seagate.com/releases/cortx/rgw-build/release/${release_tag}/"),
                                            string(name: 'GITHUB_PUSH', value: "yes"),
                                            string(name: 'TAG_LATEST', value: "yes"),
                                            string(name: 'DOCKER_REGISTRY', value: "cortx-docker.colo.seagate.com"),
                                            string(name: 'OS', value: "${os_version}"),
                                            string(name: 'CORTX_IMAGE', value: "cortx-rgw"),
                                            string(name: 'EMAIL_RECIPIENTS', value: "DEBUG")
                                            ]
                        env.cortx_rgw_image = build_cortx_rgw_image.buildVariables.image
                        } catch (err) {
                            build_stage = env.STAGE_NAME
                            error "Failed to Build CORTX-RGW image"
                        }
                    }
                }
            }

        stage ('Print Build Information') {
            steps {
                script { build_stage = env.STAGE_NAME }

            sh label: 'Print Release Build and ISO location', script:'''
                echo "CORTX RGW build is available at "
                echo "http://cortx-storage.colo.seagate.com/releases/cortx/rgw-build/release/$release_tag/"
                echo "CORTX-RGW image is available at,"
                echo "${cortx_rgw_image}"
                '''
            }
        } 
    }

    post {
        always {
            cleanWs()
        }
    }
}