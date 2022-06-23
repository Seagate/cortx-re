#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label "ceph-build-node"
        }
    }
    
    environment {
        BUILD_LOCATION = "/root/custom-ci-jenkins/cortx-rgw-build/${BUILD_NUMBER}"
        BUILD_OS = "rockylinux-8.4"
        VM_BUILD = false
        CORTX_RGW_OPTIMIZED_BUILD = true

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
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts.', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts.', trim: true)
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

        stage('Checkout Script') {
            when { expression { params.BUILD_LATEST_CORTX_RGW == 'yes' } }
            steps {
                cleanWs()
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])
                }
            }
        }

        stage ('Build CORTX-RGW Binary Packages') {
            when { expression { params.BUILD_LATEST_CORTX_RGW == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build Binary Packages', script: """
                pushd solutions/kubernetes/
                    export CEPH_REPO=${CORTX_RGW_URL}
                    export CEPH_BRANCH=${CORTX_RGW_BRANCH}
                    export CORTX_RGW_OPTIMIZED_BUILD=${CORTX_RGW_OPTIMIZED_BUILD}
                    bash ceph-binary-build.sh --ceph-build-env ${BUILD_LOCATION}
                popd
                """
            }
        }

        stage ('Upload') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    mkdir -p $build_upload_dir
                    if [ "$BUILD_LATEST_CORTX_RGW" == "yes" ]; then
                        cp $BUILD_LOCATION/rpmbuild/$BUILD_NUMBER/RPMS/*/*.rpm $build_upload_dir
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