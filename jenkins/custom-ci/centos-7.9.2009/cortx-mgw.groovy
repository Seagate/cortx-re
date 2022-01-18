#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label 'ceph-build-node'
        }
    }

    parameters {  
        string(name: 'CEPH_URL', defaultValue: 'https://github.com/ceph/ceph', description: 'Repository URL for ceph build')
        string(name: 'CEPH_BRANCH', defaultValue: 'master', description: 'Branch for ceph build')
    }
    
    options {
        timestamps() 
    }

    stages {
    
        stage('Checkout ceph') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('ceph') {
                checkout([$class: 'GitSCM', branches: [[name: "${CEPH_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CEPH_URL}"]]])
                }
            }
        }

        stage ('Clean up') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Clean up', script: '''
                rm -rf /mnt/rgw-build/rpmbuild/SOURCES/ceph*.tar.bz2
                rm -rf  /mnt/rgw-build/rpmbuild/BUILD/ceph*
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
                    ls -la /mnt/rgw-build/rpmbuild/$BUILD_NUMBER/RPMS/*/*.rpm
                    createrepo -v /mnt/rgw-build/rpmbuild/$BUILD_NUMBER/RPMS/
                    echo "Ceph Packages are available at "
                    echo "http://cortx-storage.colo.seagate.com/releases/cortx/rgw-build/rpmbuild/$BUILD_NUMBER/RPMS/"
                '''
            }
        }
    }
}