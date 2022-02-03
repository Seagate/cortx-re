#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label 'ceph-build-node'
        }
    }

    triggers { cron('30 22 * * *') }

    environment {
        version = "2.0.0"
    }

    parameters {
        string(name: 'MOTR_URL', defaultValue: 'https://github.com/Seagate/cortx-motr', description: 'Repository URL for Motr build')
        string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch for Motr build')
        string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Repository URL for Hare build')
        string(name: 'HARE_BRANCH', defaultValue: 'main', description: 'Branch for Hare build')
        string(name: 'UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'Repository URL for Utils build')
        string(name: 'UTILS_BRANCH', defaultValue: 'main', description: 'Branch for Utils build')
        string(name: 'CEPH_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw', description: 'Repository URL for ceph build')
        string(name: 'CEPH_BRANCH', defaultValue: 'main', description: 'Branch for ceph build')
    }
    
    options {
        timestamps() 
    }

    stages {
    
        stage('Checkout Ceph') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('ceph') {
                checkout([$class: 'GitSCM', branches: [[name: "${CEPH_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CEPH_URL}"]]])
                }
            }
        }

        stage('Checkout Motr') {
            steps {
                dir ('motr') {
                    checkout([$class: 'GitSCM', branches: [[name: "$MOTR_BRANCH"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "$MOTR_URL"]]])
                }    
            }
        }

        stage('Checkout py-utils') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('cortx-py-utils') {
                    checkout([$class: 'GitSCM', branches: [[name: "${UTILS_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "$UTILS_URL"]]])
                }
            }
        }

        stage('Checkout Hare') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('hare') {
                    checkout([$class: 'GitSCM', branches: [[name: "${HARE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', noTags: true, reference: '', shallow: true], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', shallow: true, trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', refspec: '+refs/heads/main:refs/remotes/origin/main', url: "$HARE_URL"]]])
                }
            }
        }

        stage ('Clean up') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Clean up', script: '''
                rm -rf /root/rpmbuild/RPMS/x86_64/*.rpm
                rm -rf /mnt/rgw-build/rpmbuild/SOURCES/ceph*.tar.bz2
                rm -rf  /mnt/rgw-build/rpmbuild/BUILD/ceph*
                yum erase cortx-py-utils cortx-motr{,-devel} -y
                '''
            }
        }
        
        stage ('Prepare') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'prepare', script: '''
                yum install createrepo systemd-devel libuuid libuuid-devel libaio-devel openssl openssl-devel perl-File-Slurp gcc cmake3 cmake rpm-build rpmdevtools autoconf automake libtool gcc-c++ -y 
                rpmdev-setuptree
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
                rpm -ivh --nodeps ./cortx-py-utils/py-utils/dist/cortx-py-utils-2.0.0*.noarch.rpm
                mv ./cortx-py-utils/py-utils/dist/cortx-py-utils-2.0.0*.noarch.rpm /root/rpmbuild/RPMS/x86_64/
                '''
            }
        }

        stage ('Build Motr Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Install Motr', script: '''
                pushd motr
                        yum install perl-YAML-LibYAML perl-List-MoreUtils perl-XML-LibXML castxml perl-File-Find-Rule perl-IO-All asciidoc libedit-devel python2-devel -y
                        cp cortx-motr.spec.in cortx-motr.spec
                        sed -i -e 's/@.*@/111/g' -e '/^111/d' cortx-motr.spec
                        #yum-builddep -y cortx-motr.spec
                        ./autogen.sh
                        ./configure --with-user-mode-only
                        export build_number=${BUILD_ID}
                        make rpms
                        rm -rf /root/rpmbuild/RPMS/x86_64/*debug*.rpm
                        yum install /root/rpmbuild/RPMS/x86_64/cortx-motr{,-devel}*.rpm -y --nogpgcheck
                popd
                '''
            }
        }


        stage ('Build Hare Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Install Motr', script: '''

                pushd hare
                    export build_number=${BUILD_ID}
                    make VERSION=$version rpm
                popd
            '''
            }
        }
    
        stage('Build Ceph Package') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build', script: '''

                pushd ceph
                    sed -i 's/centos|/rocky|centos|/' install-deps.sh
                    ./install-deps.sh
                    ./make-dist
                    mkdir -p /mnt/rgw-build/rpmbuild/$BUILD_NUMBER/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
                    mv ceph*.tar.bz2 /mnt/rgw-build/rpmbuild/$BUILD_NUMBER/SOURCES/
                    tar --strip-components=1 -C /mnt/rgw-build/rpmbuild/$BUILD_NUMBER/SPECS/ --no-anchored -xvjf /mnt/rgw-build/rpmbuild/$BUILD_NUMBER/SOURCES/ceph*.tar.bz2 "ceph.spec"
                popd

                pushd /mnt/rgw-build/rpmbuild/$BUILD_NUMBER
                     rpmbuild --clean --rmsource --define "_unpackaged_files_terminate_build 0" --define "debug_package %{nil}" --without cmake_verbose_logging --without jaeger --without lttng --without seastar --without kafka_endpoint --without zbd --without cephfs_java --without cephfs_shell --without ocf --without selinux --without ceph_test_package --without make_check --define "_binary_payload w2T16.xzdio" --define "_topdir `pwd`" -ba ./SPECS/ceph.spec
                popd
            '''    
            }
        }    

        stage ('Copy RPMS') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    mkdir -p /mnt/rgw-build/release/$BUILD_NUMBER
                    mv /mnt/rgw-build/rpmbuild/$BUILD_NUMBER/RPMS/*/*.rpm /mnt/rgw-build/release/$BUILD_NUMBER/
                    mv /root/rpmbuild/RPMS/x86_64/*.rpm /mnt/rgw-build/release/$BUILD_NUMBER/
                    createrepo -v /mnt/rgw-build/release/$BUILD_NUMBER/
                    echo "Ceph and Motr Packages are available at "
                    echo "http://cortx-storage.colo.seagate.com/releases/cortx/rgw-build/release/$BUILD_NUMBER/"
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