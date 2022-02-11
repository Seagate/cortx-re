#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }
    
    environment { 
        component = "cortx-rgw"
        branch = "custom-ci"
        release_dir = "/mnt/bigstorage/releases/cortx"
        release_tag = "custom-build-$CUSTOM_CI_BUILD_ID"
        build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/$release_tag/cortx_iso"
    }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps ()
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '10'))
    }
    
    parameters {  
        string(name: 'CORTX_RGW_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw', description: 'Repository URL for cortx-rgw build')
        string(name: 'CORTX_RGW_BRANCH', defaultValue: 'main', description: 'Branch for cortx-rgw build')
        string(name: 'CUSTOM_CI_BUILD_ID', defaultValue: '0', description: 'Custom CI Build Number')
        // Add os_version parameter in jenkins configuration
    }    


    stages {

        stage('Checkout cortx-rgw') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('cortx-rgw') {
                checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RGW_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RGW_URL}"]]])
                }
            }
        }
        
        stage('Install Dependencies') {
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Build', script: '''                    
                #sed -i -e \'s/Library/Production\\/Rocky_8_Content_View/g\' -e  \'/http.*EPEL/s/Rocky_8\\/EPEL/EPEL-8\\/EPEL-8/g\' /etc/yum.repos.d/R8.4.repo

                pushd cortx-rgw
                    ./install-deps.sh
                    ./make-dist
                    mkdir -p /mnt/rgw/$BUILD_NUMBER/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
                    tar --strip-components=1 -C /mnt/rgw/$BUILD_NUMBER/SPECS/ --no-anchored -xvjf ceph-*tar.bz2 "ceph.spec"
                    mv ceph*tar.bz2 /mnt/rgw/$BUILD_NUMBER/SOURCES/
                popd
                '''

                sh label: 'Configure yum repositories', script: """
                    set +x
                    yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/$os_version/$release_tag/cortx_iso/
                    yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*
                    yum clean all;rm -rf /var/cache/yum
                    yum install cortx-motr{,-devel} -y
                """
            }
        }

        stage('Build cortx-rgw packages') {
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Build', script: '''
                cd /mnt/rgw/$BUILD_NUMBER
                rpmbuild --clean --rmsource --define "_unpackaged_files_terminate_build 0" --define "debug_package %{nil}" --without cmake_verbose_logging --without jaeger --without lttng --without seastar --without kafka_endpoint --without zbd --without cephfs_java --without cephfs_shell --without ocf --without selinux --without ceph_test_package --without make_check --define "_binary_payload w2T16.xzdio" --define "_topdir `pwd`" -ba /mnt/rgw/$BUILD_NUMBER/SPECS/ceph.spec
                '''
            }
        }

        stage ('Upload') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    mkdir -p $build_upload_dir
                    cp /mnt/rgw/$BUILD_NUMBER/RPMS/*/*.rpm $build_upload_dir
                '''
            }
        }
    }
}