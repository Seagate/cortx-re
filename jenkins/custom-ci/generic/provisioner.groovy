#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }

    parameters {
        string(name: 'PRVSNR_URL', defaultValue: 'https://github.com/Seagate/cortx-prvsnr', description: 'Repository URL for Provisioner.')
        string(name: 'PRVSNR_BRANCH', defaultValue: 'main', description: 'Branch for Provisioner.')
        string(name: 'CUSTOM_CI_BUILD_ID', defaultValue: '0', description: 'Custom CI Build Number')
        choice(
            name: 'BUILD_LATEST_CORTX_PROVISIONER',
            choices: ['yes', 'no'],
            description: 'Build cortx-provisioner from latest code or use last-successful build.'
        )
        // Add os_version parameter in jenkins configuration
    }

    environment {
        component = "provisioner"
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
            when { expression { params.BUILD_LATEST_CORTX_PROVISIONER == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }
                    checkout([$class: 'GitSCM', branches: [[name: "${PRVSNR_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${PRVSNR_URL}"]]])
            }
        }

        stage('Build') {
            when { expression { params.BUILD_LATEST_CORTX_PROVISIONER == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }

                sh encoding: 'UTF-8', label: 'cortx-provisioner', script: '''
                if [ -f "./jenkins/build.sh" ]; then
                    bash ./jenkins/build.sh -v 2.0.0 -b ${CUSTOM_CI_BUILD_ID}
                else
                    echo "cortx-provisioner package creation is not implemented"
                fi
                '''
            }
        }

        stage ('Upload') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    mkdir -p $build_upload_dir
                    shopt -s extglob
                    if [ "$BUILD_LATEST_CORTX_PROVISIONER" == "yes" ]; then
                        if ls ./dist/*.rpm; then
                            cp ./dist/!(*.src.rpm|*.tar.gz) $build_upload_dir
                        fi
                        createrepo -v --update $build_upload_dir
                    else
                        echo "Copy packages form last_successful"
                        cp /mnt/bigstorage/releases/cortx/components/github/main/rockylinux-8.4/dev/provisioner/last_successful/*.rpm $build_upload_dir
                    fi    
                '''
            }
        }
    }
}