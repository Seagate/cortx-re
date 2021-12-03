#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label 'docker-centos-7.9.2009-node'
        }
    }

    environment {
        version = "2.0.0"
        branch = "custom-ci"
        os_version = "centos-7.9.2009"
        release_dir = "/mnt/bigstorage/releases/cortx"
        integration_dir = "$release_dir/github/integration-custom-ci/$os_version/"
        release_tag = "custom-build-$BUILD_ID"
        passphrase = credentials('rpm-sign-passphrase')
        python_deps = "${THIRD_PARTY_PYTHON_VERSION == 'cortx-2.0' ? "$release_dir/third-party-deps/python-deps/python-packages-2.0.0-latest" : THIRD_PARTY_PYTHON_VERSION == 'cortx-1.0' ?  "$release_dir/third-party-deps/python-packages" : "$release_dir/third-party-deps/python-deps/python-packages-2.0.0-custom"}"
        cortx_os_iso = "/mnt/bigstorage/releases/cortx_builds/custom-os-iso/cortx-2.0.0/cortx-os-2.0.0-7.iso"
        third_party_dir = "${THIRD_PARTY_RPM_VERSION == 'cortx-2.0' ? "$release_dir/third-party-deps/centos/$os_version-2.0.0-latest" : THIRD_PARTY_RPM_VERSION == 'cortx-2.0-k8' ?  "$release_dir/third-party-deps/centos/$os_version-2.0.0-k8" : "$release_dir/third-party-deps/centos/$os_version-custom"}"
    }

    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
        parallelsAlwaysFailFast()
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '10'))
    }

    parameters {
        string(name: 'CSM_AGENT_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CSM Agent', trim: true)
        string(name: 'CSM_AGENT_URL', defaultValue: 'https://github.com/Seagate/cortx-manager', description: 'CSM_AGENT Repository URL', trim: true)
        string(name: 'HARE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Hare', trim: true)
        string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Hare Repository URL', trim: true)
        string(name: 'HA_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cortx-HA', trim: true)
        string(name: 'HA_URL', defaultValue: 'https://github.com/Seagate/cortx-ha.git', description: 'Cortx-HA Repository URL', trim: true)
        string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Motr', trim: true)
        string(name: 'MOTR_URL', defaultValue: 'https://github.com/Seagate/cortx-motr.git', description: 'Motr Repository URL', trim: true)
        string(name: 'PRVSNR_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Provisioner', trim: true)
        string(name: 'PRVSNR_URL', defaultValue: 'https://github.com/Seagate/cortx-prvsnr.git', description: 'Provisioner Repository URL', trim: true)
        string(name: 'S3_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for S3Server', trim: true)
        string(name: 'S3_URL', defaultValue: 'https://github.com/Seagate/cortx-s3server.git', description: 'S3Server Repository URL', trim: true)
        string(name: 'CORTX_UTILS_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Utils', trim: true)
        string(name: 'CORTX_UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'CORTX Utils Repository URL', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX RE', trim: true)
        string(name: 'CORTX_RE_URL', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'CORTX RE Repository URL', trim: true)

        choice(
            name: 'THIRD_PARTY_RPM_VERSION',
            choices: ['cortx-2.0-k8', 'cortx-2.0', 'custom'],
            description: 'Third Party RPM Version to use.'
        )

        choice(
            name: 'THIRD_PARTY_PYTHON_VERSION',
            choices: ['cortx-2.0', 'custom'],
            description: 'Third Party Python Version to use.'
        )


        choice(
            name: 'ISO_GENERATION',
            choices: ['no', 'yes'],
            description: 'Need ISO files'
        )

    }

    stages {
       
       stage ("Build CORTX-ALL image") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        def build_cortx_all_image = build job: '/Release_Engineering/re-workspace/sv_space/sv-cortx-all-image', wait: true,
                                    parameters: [
                                        string(name: 'CORTX_RE_URL', value: "https://github.com/shailesh-vaidya/cortx-re"),
                                        string(name: 'CORTX_RE_BRANCH', value: "local-registry"),
                                        string(name: 'BUILD', value: "custom-build-5009"),
                                        string(name: 'GITHUB_PUSH', value: "yes"),
                                        string(name: 'TAG_LATEST', value: "yes"),
                                        string(name: 'DOCKER_REGISTRY', value: "cortx-docker.colo.seagate.com"),
                                        string(name: 'EMAIL_RECIPIENTS', value: "DEBUG")
                                        ]
                    env.cortx_all_image = build_cortx_all_image.buildVariables.image
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build CORTX-ALL image"
                    }
                }
            }
        } 

    }

    post {

        success {
                sh label: 'Delete Old Builds', script: '''
                set +x
                find ${integration_dir}/* -maxdepth 0 -mtime +5 -type d -exec rm -rf {} \\;
                '''
        }

        always {
            script {
                env.release_build_location = "http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/${env.os_version}/${env.release_tag}"
                env.release_build = "${env.release_tag}"
                env.build_stage = "${build_stage}"
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]

                def mailRecipients = "shailesh.vaidya@seagate.com"
                emailext (
                    body: '''${SCRIPT, template="K8s-release-email.template"}''',
                    mimeType: 'text/html',
                    subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                    attachLog: true,
                    to: "${mailRecipients}",
                    recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}