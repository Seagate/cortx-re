#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label 'docker-centos-7.9.2009-node'
        }
    }
    
    environment {
        component = "cortx-ha"
        branch = "custom-ci" 
        os_version = "centos-7.9.2009"
        release_dir = "/mnt/bigstorage/releases/cortx"
        release_tag = "custom-build-$CUSTOM_CI_BUILD_ID"
        build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/$release_tag/cortx_iso"
    python_deps = "${THIRD_PARTY_PYTHON_VERSION == 'cortx-2.0' ? "python-packages-2.0.0-latest" : THIRD_PARTY_PYTHON_VERSION == 'custom' ?  "python-packages-2.0.0-custom" : "python-packages-2.0.0-stable"}"
    }
    
    parameters {
        string(name: 'HA_URL', defaultValue: 'https://github.com/Seagate/cortx-ha', description: 'Repository URL to be used for cortx-ha build.')
        string(name: 'HA_BRANCH', defaultValue: 'stable', description: 'Branch to be used for cortx-ha build.')
        string(name: 'CUSTOM_CI_BUILD_ID', defaultValue: '0', description: 'Custom CI Build Number')
    string(name: 'CORTX_UTILS_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Utils', trim: true)
    string(name: 'CORTX_UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'CORTX Utils Repository URL', trim: true)
    string(name: 'THIRD_PARTY_PYTHON_VERSION', defaultValue: 'custom', description: 'Third Party Python Version to use', trim: true)
    }
    
    
    options {
        timeout(time: 35, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '10'))
    }

    stages {
        stage('Checkout') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('cortx-ha') {
                    checkout([$class: 'GitSCM', branches: [[name: "$HA_BRANCH"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: false, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "$HA_URL"]]])
                }
                script {
                    version =  sh (script: 'cat ./cortx-ha/VERSION', returnStdout: true).trim()
                    env.version = version
                }
            }
        }
    

        // Install third-party dependencies. This needs to be removed once components move away from self-contained binaries 
        stage('Install python packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: '', script: '''
        CORTX_UTILS_REPO_OWNER=$(echo $CORTX_UTILS_URL | cut -d "/" -f4)
                echo "VERSION: $version"
                yum erase python36-PyYAML -y
                cat <<EOF >>/etc/pip.conf
[global]
timeout: 60
index-url: http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/python-deps/$python_deps/
trusted-host: cortx-storage.colo.seagate.com
EOF
                pip3 install -r https://raw.githubusercontent.com/$CORTX_UTILS_REPO_OWNER/cortx-utils/$CORTX_UTILS_BRANCH/py-utils/python_requirements.txt
                pip3 install -r https://raw.githubusercontent.com/$CORTX_UTILS_REPO_OWNER/cortx-utils/$CORTX_UTILS_BRANCH/py-utils/python_requirements.ext.txt
                rm -rf /etc/pip.conf
            '''        
            }
        }


        stage('Install Dependencies') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: '', script: '''
                    yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/$os_version/$release_tag/cortx_iso/
                    yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*

                    yum clean all && rm -rf /var/cache/yum
                    pushd $component
                        yum erase python36-PyYAML -y
                        bash jenkins/cicd/cortx-ha-dep.sh
                        pip3 install numpy
                    popd
                '''
            }
        }

        stage('Build') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build', script: '''
                    pushd $component
                        echo "Executing build script"
                        ./jenkins/build.sh -b $CUSTOM_CI_BUILD_ID
                    popd
                '''    
            }
        }
        
        stage('Test') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Test', script: '''
                    set -xe
                    pushd $component
                        yum localinstall $WORKSPACE/$component/dist/rpmbuild/RPMS/*/cortx-ha-*.rpm -y
                        bash jenkins/cicd/cortx-ha-cicd.sh
                    popd
                '''    
            }
        }

        stage ('Upload') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    mkdir -p $build_upload_dir
                    cp $WORKSPACE/cortx-ha/dist/rpmbuild/RPMS/*/*.rpm $build_upload_dir
                '''
            }
        } 
    }
}
