#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label 'ceph-build-node'
        }
    }

    parameters {
        string(name: 'MOTR_URL', defaultValue: 'https://github.com/Seagate/cortx-motr', description: 'Repository URL for Motr build')
        string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch for Motr build')
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

        stage ('Clean up') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Clean up', script: '''
                rm -rf /root/rpmbuild/RPMS/x86_64/*.rpm
                rm -rf /mnt/rgw-build/rpmbuild/SOURCES/ceph*.tar.bz2
                rm -rf  /mnt/rgw-build/rpmbuild/BUILD/ceph*
                yum erase cortx-motr{,-devel} -y
                '''
            }
        }

        stage ('Build Motr Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Install Motr', script: '''
                pushd motr
                        yum install castxml perl-File-Find-Rule perl-IO-All asciidoc libedit-devel python2-devel -y
                        cp cortx-motr.spec.in cortx-motr.spec
                        sed -i -e 's/@.*@/111/g' -e '/^111/d' cortx-motr.spec
                        #yum-builddep -y cortx-motr.spec
                        ./autogen.sh
                        ./configure --with-user-mode-only
                        export build_number=${BUILD_ID}
                        make rpms
                        rm -rf /root/rpmbuild/RPMS/x86_64/*debug*.rpm
                        yum install /root/rpmbuild/RPMS/x86_64/cortx-motr{,-devel}*.rpm -y --nogpgcheck
                '''
            }
        }

    
        stage('Build') {
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
                    echo "http://cortx-storage.colo.seagate.com/releases/cortx/rgw-build/$BUILD_NUMBER/"
                '''
            }
        }
    }
}