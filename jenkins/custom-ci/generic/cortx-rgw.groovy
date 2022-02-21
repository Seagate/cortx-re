#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label "ceph-build-node"
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

        choice(
            name: 'BUILD_LATEST_CORTX_RGW',
            choices: ['yes', 'no'],
            description: 'Build cortx-rgw from latest code or use last-successful build.'
        )
    }    


    stages {

        stage('Checkout cortx-rgw') {
            when { expression { params.BUILD_LATEST_CORTX_RGW == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('cortx-rgw') {
                checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RGW_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RGW_URL}"]]])
                }
            }
        }
        
        stage('Install Dependencies') {
            when { expression { params.BUILD_LATEST_CORTX_RGW == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Build', script: '''
                rm -f /etc/yum.repos.d/cortx-storage.colo.seagate.com* /etc/yum.repos.d/root_rpmbuild_RPMS_x86_64.repo
                yum install bzip2 rpm-build -y

                pushd cortx-rgw
                    ./install-deps.sh
                    ./make-dist
                    mkdir -p $release_dir/$component/$branch/rpmbuild/$BUILD_NUMBER/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
                    tar --strip-components=1 -C $release_dir/$component/$branch/rpmbuild/$BUILD_NUMBER/SPECS/ --no-anchored -xvjf ceph-*tar.bz2 "ceph.spec"
                    mv ceph*tar.bz2 $release_dir/$component/$branch/rpmbuild/$BUILD_NUMBER/SOURCES/
                popd
                '''

                sh label: 'Configure yum repositories', script: """
                    set +x
                    yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/$os_version/$release_tag/cortx_iso/
                    yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/rockylinux/rockylinux-8.4-2.0.0-latest/
                    yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*
                    yum clean all;rm -rf /var/cache/yum
                    yum install cortx-motr{,-devel} -y
                """
            }
        }

        stage('Build cortx-rgw packages') {
            when { expression { params.BUILD_LATEST_CORTX_RGW == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Build', script: '''
                cd $release_dir/$component/$branch/rpmbuild/$BUILD_NUMBER
                rpmbuild --clean --rmsource --define "_unpackaged_files_terminate_build 0" --define "debug_package %{nil}" --without cmake_verbose_logging --without jaeger --without lttng --without seastar --without kafka_endpoint --without zbd --without cephfs_java --without cephfs_shell --without ocf --without selinux --without ceph_test_package --without make_check --define "_binary_payload w2T16.xzdio" --define "_topdir `pwd`" -ba ./SPECS/ceph.spec
                '''
            }
        }

        stage ('Upload') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    mkdir -p $build_upload_dir
                    if [ "$BUILD_LATEST_CORTX_RGW" == "yes" ]; then
                        cp $release_dir/$component/$branch/rpmbuild/$BUILD_NUMBER/RPMS/*/*.rpm $build_upload_dir
                    else
                        echo "Copy packages form last_successful"
                        cp /mnt/bigstorage/releases/cortx/components/github/main/rockylinux-8.4/dev/cortx-rgw/last_successful/*.rpm $build_upload_dir
                    fi
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