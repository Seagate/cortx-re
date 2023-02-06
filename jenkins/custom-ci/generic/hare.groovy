#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }

    parameters {  
        string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare/', description: 'Repository URL for Hare build')
        string(name: 'CORTX_UTILS_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Utils', trim: true)
        string(name: 'CORTX_UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'CORTX Utils Repository URL', trim: true)
        string(name: 'THIRD_PARTY_PYTHON_VERSION', defaultValue: 'custom', description: 'Third Party Python Version to use', trim: true)
        string(name: 'HARE_BRANCH', defaultValue: 'main', description: 'Branch for Hare build')
        string(name: 'CUSTOM_CI_BUILD_ID', defaultValue: '0', description: 'Custom CI Build Number')
        choice(
                name: 'MOTR_BRANCH', 
                choices: ['custom-ci', 'stable', 'Cortx-v1.0.0_Beta'],
                description: 'Branch name to pick-up other components rpms'
            )
        choice(
            name: 'BUILD_LATEST_HARE',
                choices: ['yes', 'no'],
                description: 'Build cortx-Hare from latest code or use last-successful build.'
            )
        // Add os_version parameter in jenkins configuration    
    }
    

       environment {
        release_dir = "/mnt/bigstorage/releases/cortx"
        branch = "custom-ci"
        component = "hare"
        release_tag = "custom-build-$CUSTOM_CI_BUILD_ID"
        build_upload_dir = "$release_dir/github/integration-custom-ci/$os_version/$release_tag/cortx_iso"
        python_deps = "${THIRD_PARTY_PYTHON_VERSION == 'cortx-2.0' ? "python-packages-2.0.0-latest" : THIRD_PARTY_PYTHON_VERSION == 'custom' ?  "python-packages-2.0.0-custom" : "python-packages-2.0.0-stable"}"
    }
    
    
    
    options {
        timeout(time: 35, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '10'))
    }

    stages {
    
        stage('Checkout hare') {
            when { expression { params.BUILD_LATEST_HARE == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh 'mkdir -p hare'
                dir ('hare') {
                    checkout([$class: 'GitSCM', branches: [[name: "${HARE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false, timeout: 15], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false, timeout: 15]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${HARE_URL}"]]])
                }
            }
        }
    
        stage('Install Dependencies') {
            when { expression { params.BUILD_LATEST_HARE == 'yes' } }
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Install cortx-prereq', script: '''
                    CORTX_UTILS_REPO_OWNER=$(echo $CORTX_UTILS_URL | cut -d "/" -f4)
                    yum erase python36-PyYAML -y
                    cat <<EOF >>/etc/pip.conf
[global]
timeout: 60
index-url: http://ssc-nfs-cicd1.colo.seagate.com/releases/cortx/third-party-deps/python-deps/$python_deps/
trusted-host: ssc-nfs-cicd1.colo.seagate.com
EOF
                    pip3 install -r https://raw.githubusercontent.com/$CORTX_UTILS_REPO_OWNER/cortx-utils/$CORTX_UTILS_BRANCH/py-utils/python_requirements.txt
                    pip3 install -r https://raw.githubusercontent.com/$CORTX_UTILS_REPO_OWNER/cortx-utils/$CORTX_UTILS_BRANCH/py-utils/python_requirements.ext.txt
                    rm -rf /etc/pip.conf
                    
                '''
                sh label: 'Install Dependencies', script: '''
                    set +x
                    yum-config-manager --add-repo=http://ssc-nfs-cicd1.colo.seagate.com/releases/cortx/github/integration-custom-ci/$os_version/$release_tag/cortx_iso/
                    yum-config-manager --save --setopt=ssc-nfs-cicd1*.gpgcheck=1 ssc-nfs-cicd1* && yum-config-manager --save --setopt=ssc-nfs-cicd1*.gpgcheck=0 ssc-nfs-cicd1*
                    yum clean all;rm -rf /var/cache/yum
                    yum install cortx-py-utils cortx-motr{,-devel} -y --nogpgcheck
                '''
            }
        }

        stage('Build') {
            when { expression { params.BUILD_LATEST_HARE == 'yes' } }
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
                    if [ "$BUILD_LATEST_HARE" == "yes" ]; then
                            cp /root/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir
                    else
                        echo "Copy packages form last_successful"
                        cp /mnt/bigstorage/releases/cortx/components/github/main/rockylinux-8.4/dev/hare/last_successful/*.rpm $build_upload_dir
                    fi
                '''
            }
        }

    }
}