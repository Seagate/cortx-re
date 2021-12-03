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

                sh label: 'Install cortx-prereq package', script: '''
                                        cortx_iso_path=$( echo $integration_dir | sed  's/[/]mnt[/]bigstorage[/]//g' )
                                        echo $cortx_iso_path
                                        pip3 uninstall pip -y && yum reinstall python3-pip -y && ln -s /usr/bin/pip3 /usr/local/bin/pip3
                                        yum install yum-utils -y
                                        yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/centos/$third_party_rpm_dir/
                                        yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/$cortx_iso_path/cortx_iso/
                                        yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*
                                        yum clean all && rm -rf /var/cache/yum
                                        cat <<EOF >>/etc/pip.conf
[global]
timeout: 60
index-url: http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/python-deps/$python_deps
trusted-host: cortx-storage.colo.seagate.com
EOF
                                        yum install java-1.8.0-openjdk-headless -y && yum install cortx-prereq -y --nogpgcheck 
                                        rm -rf  /etc/yum.repos.d/cortx-storage.colo.seagate.com_releases_cortx_* /etc/pip.conf
                                '''

                sh label: 'Configure yum repositories', script: """
                    yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/$os_version/$release_tag/cortx_iso/
                    yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*
                    yum clean all && rm -rf /var/cache/yum
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