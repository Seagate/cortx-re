pipeline {
    agent {
        node {
            label 'docker-rockylinux-8.4-node'
        }
    }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
        disableConcurrentBuilds()   
    }

    parameters {
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VM details to be used. First node will be used as Primary node. Please provide a minimum of 3 nodes', name: 'hosts')
        text(defaultValue: '''/dev/sdX''', description: 'Disks for OSD on VMs. This assumes the disk name and no. of disk is same on all nodes', name: 'OSD_Disks')
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

        stage ('Install Ceph Prerequisites') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Install Ceph Prerequisites', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        bash ceph-deploy.sh --install-prereq
                    popd
                '''
            }
        }

        stage ('Install Ceph Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Install Ceph Packages', script: '''
                    pushd solutions/kubernetes/
                        bash ceph-deploy.sh --install-ceph
                    popd
                '''
            }
        }

        stage ('Deploy Ceph Prerequisites') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploy Ceph Prerequisites', script: '''
                    pushd solutions/kubernetes/
                        echo $OSD_Disks | tr ' ' '\n' > osd_disks
                        cat osd_disks
                        bash ceph-deploy.sh --deploy-prereq
                    popd
                '''
            }
        }

        stage ('Deploy Ceph Monitor') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploy Ceph Monitor', script: '''
                    pushd solutions/kubernetes/
                        bash ceph-deploy.sh --deploy-mon
                    popd
                '''
            }
        }

        stage ('Deploy Ceph Manager') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploy Ceph Manager', script: '''
                    pushd solutions/kubernetes/
                        bash ceph-deploy.sh --deploy-mgr
                    popd
                '''
            }
        }

        stage ('Deploy OSDs') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploy Ceph OSD', script: '''
                    pushd solutions/kubernetes/
                        bash ceph-deploy.sh --deploy-osd
                    popd
                '''
            }
        }

        stage ('Deploy Ceph MDS') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploy Ceph MDS', script: '''
                    pushd solutions/kubernetes/
                        bash ceph-deploy.sh --deploy-mds
                    popd
                '''
            }
        }

        stage ('Deploy Ceph FS') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploy Ceph FS', script: '''
                    pushd solutions/kubernetes/
                        bash ceph-deploy.sh --deploy-fs
                    popd
                '''
            }
        }

        stage ('Deploy Ceph RGW') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploy Ceph RGW', script: '''
                    pushd solutions/kubernetes/
                        bash ceph-deploy.sh --deploy-rgw
                    popd
                '''
            }
        }

        stage ('IO Operation') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'IO Operation', script: '''
                    pushd solutions/kubernetes/
                        bash ceph-deploy.sh --io-operation
                    popd
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