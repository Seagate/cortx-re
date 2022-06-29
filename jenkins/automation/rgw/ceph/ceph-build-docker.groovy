pipeline {
    agent {
        node {
            label 'ceph-build-hw'
        }
    }

    triggers { cron('30 19 * * *') }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    environment {
        BUILD_LOCATION = "/var/log/ceph-build/${BUILD_NUMBER}"
        MOUNT = "cortx-storage.colo.seagate.com:/mnt/data1/releases/ceph"
        build_upload_dir = "/mnt/bigstorage/releases/ceph"
        VM_BUILD = false

        // Motr dependencies environment variables
        release_tag = "last_successful_prod"
        os_version = "rockylinux-8.4"
        branch = "main"
    }

    parameters {
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts.', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts.', trim: true)
        string(name: 'CEPH_REPO', defaultValue: 'https://github.com/ceph/ceph', description: 'Repository for Cluster Setup scripts.', trim: true)
        string(name: 'CEPH_BRANCH', defaultValue: 'quincy', description: 'Branch or GitHash for Cluster Setup scripts.', trim: true)
        booleanParam(name: 'CORTX_RGW_OPTIMIZED_BUILD', defaultValue: false, description: 'Selecting this option will enable cortx-rgw build optimization.')
        booleanParam(name: 'INSTALL_MOTR', defaultValue: false, description: 'Selecting this option will install motr for builds with motr backend.')

        choice(
            name: 'BUILD_OS',
            choices: ['rockylinux-8.4', 'ubuntu-20.04', 'centos-8'],
            description: 'OS to build binary packages for (*.deb, *.rpm).'
        )
    }    

    stages {
        stage('Checkout Script') {
            steps { 
                cleanWs()            
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])                
                }
            }
        }

        stage ('Build Ceph Binary Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build Binary Packages', script: """
                pushd solutions/kubernetes/
                    export CEPH_REPO=${CEPH_REPO}
                    export CEPH_BRANCH=${CEPH_BRANCH}
                    export BUILD_OS=${BUILD_OS}
                    export CORTX_RGW_OPTIMIZED_BUILD=${CORTX_RGW_OPTIMIZED_BUILD}
                    export INSTALL_MOTR=${INSTALL_MOTR}
                    bash ceph-binary-build.sh --ceph-build-env ${BUILD_LOCATION}
                popd
                """
            }
        }

        stage ('Upload RPMS') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Upload RPMS', script: """
                pushd solutions/kubernetes/
                    export CEPH_BRANCH=${CEPH_BRANCH}
                    export BUILD_OS=${BUILD_OS}
                    bash ceph-binary-build.sh --upload-packages ${BUILD_LOCATION} ${MOUNT}
                popd
                """
            }
        }
    }

    post {
        always {
            cleanWs()
            sh label: 'Cleanup Build Location', script: """
            rm -rf ${BUILD_LOCATION}
            """
        }
    }
}