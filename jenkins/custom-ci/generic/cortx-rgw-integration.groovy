#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }

    parameters {
        string(name: 'CORTX_RGW_INTEGRATION_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw-integration', description: 'Repository URL for cortx-rgw integration.')
        string(name: 'CORTX_RGW_INTEGRATION_BRANCH', defaultValue: 'main', description: 'Branch for cortx-rgw-integration.')
        string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Motr', trim: true)
        string(name: 'MOTR_URL', defaultValue: 'https://github.com/Seagate/cortx-motr.git', description: 'Motr Repository URL', trim: true)
        string(name: 'CORTX_RGW_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX-RGW', trim: true)
        string(name: 'CORTX_RGW_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw', description: 'CORTX-RGW Repository URL', trim: true)
        string(name: 'CUSTOM_CI_BUILD_ID', defaultValue: '0', description: 'Custom CI Build Number')
        // Add os_version parameter in jenkins configuration

        choice(
            name: 'ENABLE_ADDB_PLUGIN',
                choices: ['no', 'yes'],
                description: 'Generates addb plugin as part of cortx-rgw-integration.'
        )        
    }

    environment {
        component = "cortx-rgw-integration"
        branch = "custom-ci"
        release_dir = "/mnt/bigstorage/releases/cortx"
        release_tag = "custom-build-$CUSTOM_CI_BUILD_ID"
        build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/$release_tag/cortx_iso"
    }

    options {
        timeout(time: 15, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh 'python3 --version'
                checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RGW_INTEGRATION_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RGW_INTEGRATION_URL}"]]])
            }
        }

        stage('Build') {
            steps {
                script { build_stage = env.STAGE_NAME }

                sh encoding: 'UTF-8', label: 'cortx-provisioner', script: '''          
                    if [ "${ENABLE_ADDB_PLUGIN}" == "yes" ]; then
                        pushd ../
                        git clone $MOTR_URL -b $MOTR_BRANCH && git clone $CORTX_RGW_URL -b $CORTX_RGW_BRANCH
                        popd
                        bash ./jenkins/build.sh -v 2.0.0 -b ${CUSTOM_CI_BUILD_ID} -addb
                    else
                        bash ./jenkins/build.sh -v 2.0.0 -b ${CUSTOM_CI_BUILD_ID}
                    fi
    
                '''
            }
        }

        stage ('Upload') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    mkdir -p $build_upload_dir
                    #cp /root/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir
                    shopt -s extglob
                    if ls ./dist/*.rpm; then
                        cp ./dist/!(*.src.rpm|*.tar.gz) $build_upload_dir
                    fi
                    createrepo -v --update $build_upload_dir
                '''
            }
        }
    }
}