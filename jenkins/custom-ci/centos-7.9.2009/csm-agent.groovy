#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label 'docker-centos-7.9.2009-node'
        }
    }
    
    environment { 
        component = "csm-agent"
        branch = "custom-ci" 
        os_version = "centos-7.9.2009"
        release_dir = "/mnt/bigstorage/releases/cortx"
        release_tag = "custom-build-$CUSTOM_CI_BUILD_ID"
        build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/$release_tag/cortx_iso"
        python_deps = "${THIRD_PARTY_PYTHON_VERSION == 'cortx-2.0' ? "python-packages-2.0.0-latest" : THIRD_PARTY_PYTHON_VERSION == 'custom' ?  "python-packages-2.0.0-custom" : "python-packages-2.0.0-stable"}"
        third_party_rpm_dir = "${THIRD_PARTY_RPM_VERSION == 'cortx-2.0' ? "$os_version-2.0.0-latest" : THIRD_PARTY_RPM_VERSION == 'cortx-1.0' ?  "$os_version-1.0.0-1" : "$os_version-custom"}"
        integration_dir = "${INTEGRATION_DIR_PATH == '/mnt/bigstorage/releases/cortx/github/integration-custom-ci/centos-7.9.2009/' ? "$release_dir/github/integration-custom-ci/$os_version/$release_tag" : "/mnt/bigstorage/releases/cortx/github/main/centos-7.9.2009/last_successful_prod"}"
    }

    options {
        timeout(time: 60, unit: 'MINUTES')
        timestamps ()
            ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '10'))
    }
    
    parameters {  
        string(name: 'CSM_AGENT_URL', defaultValue: 'https://github.com/Seagate/cortx-management.git', description: 'Repository URL for cortx-management build.')
        string(name: 'CSM_AGENT_BRANCH', defaultValue: 'stable', description: 'Branch for cortx-management build.')
        string(name: 'CUSTOM_CI_BUILD_ID', defaultValue: '0', description: 'Custom CI Build Number')
        string(name: 'THIRD_PARTY_PYTHON_VERSION', defaultValue: 'cortx-2.0', description: 'Third Party Python Version to use', trim: true)
        string(name: 'THIRD_PARTY_RPM_VERSION', defaultValue: 'cortx-2.0', description: 'Third Party RPM Version to use', trim: true)
        string(name: 'INTEGRATION_DIR_PATH', defaultValue: '/mnt/bigstorage/releases/cortx/github/main/centos-7.9.2009/last_successful_prod/', description: 'Integration directory path', trim: true)
    }    


    stages {

        stage('Checkout') {
            
            steps {
                script { build_stage = env.STAGE_NAME }
                dir('cortx-manager') {
                    checkout([$class: 'GitSCM', branches: [[name: "${CSM_AGENT_BRANCH}"]], doGenerateSubmoduleConfigurations: false,  extensions: [[$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CSM_AGENT_URL}"]]])
                }
                dir('seagate-ldr') {
                    checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/seagate-ldr.git']]])
                }
                dir ('cortx-re') {
                    checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', noTags: true, reference: '', shallow: true]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]]
                }
            }
        }
        
        stage('Install Dependencies') {
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Configure yum repository for cortx-py-utils', script: """
                    yum-config-manager --nogpgcheck --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/kubernetes/centos-7.9.2009/last_successful_prod/cortx_iso/

                    pip3 install --no-cache-dir --trusted-host cortx-storage.colo.seagate.com -i http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/python-deps/python-packages-2.0.0-latest/ -r https://raw.githubusercontent.com/Seagate/cortx-utils/kubernetes/py-utils/python_requirements.txt -r https://raw.githubusercontent.com/Seagate/cortx-utils/kubernetes/py-utils/python_requirements.ext.txt
                """

                sh label: 'Install pyinstaller', script: """
                        pip3.6 install  pyinstaller==3.5
                """
            }
        }    
        
        stage('Build') {
            steps {
                script { build_stage = env.STAGE_NAME }
                // Exclude return code check for csm_setup and csm_test
                sh label: 'Build', returnStatus: true, script: '''
                pushd cortx-manager
                    BUILD=$(git rev-parse --short HEAD)
                    VERSION="2.0.0"
                    echo "Executing build script"
                    echo "VERSION:$VERSION"
                    echo "Python:$(python --version)"
                    ./cicd/build.sh -v $VERSION -b $CUSTOM_CI_BUILD_ID -t -n ldr -l $WORKSPACE/seagate-ldr
                popd    
                '''    
            }
        }
        
        stage ('Upload') {
            when { expression { false } }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    mkdir -p $build_upload_dir
                    cp ./cortx-manager/dist/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir
                '''
            }
        }
    }
}