#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }

    parameters {  
        string(name: 'CORTX_CC_URL', defaultValue: 'https://github.com/Seagate/cortx-cc', description: 'Repository URL for cortx-cc build')
        string(name: 'CORTX_CC_BRANCH', defaultValue: 'main', description: 'Branch for cortx-cc build')
        string(name: 'CORTX_UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'CORTX Utils Repository URL', trim: true)
        string(name: 'CORTX_UTILS_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Utils', trim: true)
        string(name: 'THIRD_PARTY_PYTHON_VERSION', defaultValue: 'custom', description: 'Third Party Python Version to use', trim: true)
        string(name: 'THIRD_PARTY_RPM_VERSION', defaultValue: 'custom', description: 'Third Party RPM packages Version to use', trim: true)
        string(name: 'CUSTOM_CI_BUILD_ID', defaultValue: '0', description: 'Custom CI Build Number')
        choice(
            name: 'BUILD_LATEST_CORTX_CC',
                choices: ['yes', 'no'],
                description: 'Build cortx-cc from latest code or use last-successful build.'
        )
        // Add os_version parameter in jenkins configuration    
    }
    

       environment {
        release_dir = "/mnt/bigstorage/releases/cortx"
        component = "cortx-cc"
        release_tag = "custom-build-$CUSTOM_CI_BUILD_ID"
        build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/$release_tag/cortx_iso"
        python_deps = "${THIRD_PARTY_PYTHON_VERSION == 'cortx-2.0' ? "python-packages-2.0.0-latest" : THIRD_PARTY_PYTHON_VERSION == 'custom' ?  "python-packages-2.0.0-custom" : "python-packages-2.0.0-stable"}"
        third_party_rpm_repo = "${THIRD_PARTY_RPM_VERSION == 'cortx-2.0' ? "$os_version-2.0.0-latest" : THIRD_PARTY_RPM_VERSION == 'cortx-2.0-k8' ?  "$os_version-2.0.0-k8" : "$os_version-custom"}"
    }
    
    
    
    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '10'))
    }

    stages {
    
        stage('Checkout cortx-cc') {
            when { expression { params.BUILD_LATEST_CORTX_CC == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('cortx-cc') {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_CC_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false, timeout: 15], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false, timeout: 15]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_CC_URL}"]]])
                }
            }
        }
    
        stage('Install Dependencies') {
            when { expression { params.BUILD_LATEST_CORTX_CC == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Install cortx-cc pre-requisites', script: '''
                    yum -y install python3 python3-devel facter yum-utils
                    yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/rockylinux/$third_party_rpm_repo/
                    yum -y install consul-1.9.1 --nogpgcheck
                '''

                sh label: 'Install cortx-prereq', script: '''
                    CORTX_UTILS_REPO_OWNER=$(echo $CORTX_UTILS_URL | cut -d "/" -f4)
                    yum install -y gcc python3 python3-pip python3-devel python3-setuptools openssl-devel libffi-devel python3-dbus
                    pip3 install  --no-cache-dir --trusted-host cortx-storage.colo.seagate.com -i http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/python-deps/$python_deps/ -r https://raw.githubusercontent.com/${CORTX_UTILS_REPO_OWNER}/cortx-utils/${CORTX_UTILS_BRANCH}/py-utils/python_requirements.txt -r https://raw.githubusercontent.com/${CORTX_UTILS_REPO_OWNER}/cortx-utils/${CORTX_UTILS_BRANCH}/py-utils/python_requirements.ext.txt
                    rm -rf /etc/pip.conf
                '''

                sh label: 'Install Dependencies', script: '''
                    set +x
                    yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/$os_version/$release_tag/cortx_iso/
                    yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*
                    yum clean all;rm -rf /var/cache/yum
                    yum install cortx-py-utils cortx-motr{,-devel} -y --nogpgcheck
                '''
            }
        }

        stage('Build') {
            when { expression { params.BUILD_LATEST_CORTX_CC == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build', returnStatus: true, script: '''
                    set -xe
                    pushd $component
                    echo "Executing build script"
                    export build_number=${CUSTOM_CI_BUILD_ID}
                    make rpm
                    popd
                '''    
            }
        }

        stage ('Copy RPMS') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    mkdir -p $build_upload_dir
                    if [ "$BUILD_LATEST_CORTX_CC" == "yes" ]; then
                            cp /root/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir
                    else
                        echo "Copy packages form last_successful"
                        cp ${release_dir}/components/github/main/rockylinux-8.4/dev/${component}/last_successful/*.rpm $build_upload_dir
                    fi
                '''
            }
        }

    }
}