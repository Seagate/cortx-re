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
        string(name: 'CUSTOM_CI_BUILD_ID', defaultValue: '0', description: 'Custom CI Build Number')
    }
    
    options {
        timestamps() 
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '10'))
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
    
        stage('Build') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build', script: '''

                rm -rf /mnt/rgw-build/rpmbuild/SOURCES/ceph*.tar.bz2
                rm -rf  /mnt/rgw-build/rpmbuild/BUILD/ceph*

                pushd ceph
                    sed -i 's/centos|/rocky|centos|/' install-deps.sh
                    #./install-deps.sh
                    ./make-dist
                    mkdir -p /mnt/rgw-build/rpmbuild/$BUILD_NUMBER/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
                    mv ceph*.tar.bz2 /mnt/rgw-build/rpmbuild/$BUILD_NUMBER/SOURCES/
                    tar --strip-components=1 -C /mnt/rgw-build/rpmbuild/$BUILD_NUMBER/SPECS/ --no-anchored -xvjf /mnt/rgw-build/rpmbuild/$BUILD_NUMBER/SOURCES/ceph*.tar.bz2 "ceph.spec"
                popd

                pushd /mnt/rgw-build/rpmbuild/$BUILD_NUMBER
                     rpmbuild --define "_unpackaged_files_terminate_build 0" --define "build_type rgw" --define "debug_package %{nil}" --without prometheus-alerts --without grafana-dashboards --without  mgr-dashboard --without cmake_verbose_logging --without jaeger --without lttng --without seastar --without kafka_endpoint --without zbd --without cephfs_java --without cephfs_shell --without ocf --without selinux --without ceph_test_package --without make_check --define "_binary_payload w2T16.xzdio" --define "_topdir `pwd`" -ba ./SPECS/ceph.spec
                popd

                echo "Packages are available at "
                ls -la /mnt/rgw-build/rpmbuild/$BUILD_NUMBER/RPMS/*/*.rpm
            '''    
            }
        }    

        stage ('Copy RPMS') {
            when { expression { false } }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    mkdir -p $build_upload_dir
                    shopt -s extglob
                    cp ./py-utils/dist/!(*.src.rpm|*.tar.gz) $build_upload_dir
                    cp ./statsd-utils/dist/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir
                    createrepo -v --update $build_upload_dir
                '''
            }
        }

    }
}