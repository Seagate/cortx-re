pipeline {
    agent {
        node {
            label 'docker-centos-7.9.2009-node'
        }
    }
    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '50', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    environment {
        GITHUB_CRED = credentials('shailesh-github-token')
    }

    parameters {

        string(name: 'SEAGATE_TOOLS_BRANCH', defaultValue: 'perfline_v0.4.0', description: 'Branch or GitHash for Perfline deployment', trim: true )
        string(name: 'SEAGATE_TOOLS_REPO', defaultValue: 'https://github.com/Seagate/seagate-tools.git', description: 'Repository for Perfline Setup scripts', trim: true)
        string(name: 'PERFLINE_WORKLOADS_DIR', defaultValue: "workloads/jenkins/", description: 'specify the location of your workload directory', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_SERVER_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-rgw:2.0.0-latest', description: 'CORTX-SERVER image', trim: true)
        string(name: 'CORTX_DATA_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-data:2.0.0-latest', description: 'CORTX-DATA image', trim: true)
        string(name: 'CORTX_CONTROL_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-control:2.0.0-latest', description: 'CORTX-CONTROL image', trim: true)
        choice(
            name: 'DEPLOYMENT_METHOD',
            choices: ['standard', 'data-only'],
            description: 'Method to deploy required CORTX service. standard method will deploy all CORTX services'
        )
        string(name: 'SNS_CONFIG', defaultValue: '1+0+0', description: 'sns configuration for deployment. Please select value based on disks available on nodes.', trim: true)
        string(name: 'DIX_CONFIG', defaultValue: '1+0+0', description: 'dix configuration for deployment. Please select value based on disks available on nodes.', trim: true)
        string(name: 'CONTROL_EXTERNAL_NODEPORT', defaultValue: '31169', description: 'Port to be used for control service.', trim: true)
        string(name: 'S3_EXTERNAL_HTTP_NODEPORT', defaultValue: '30080', description: 'HTTP Port to be used for IO service.', trim: true)
        string(name: 'S3_EXTERNAL_HTTPS_NODEPORT', defaultValue: '30443', description: 'HTTPS to be used for IO service.', trim: true)
        string(name: 'NAMESPACE', defaultValue: 'cortx', description: 'kubernetes namespace to be used for CORTX deployment.', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'CORTX cluster details to be used for perfline setup and run workloads. First node will be used as Primary node', name: 'hosts')
        choice(
            name: 'EXTERNAL_EXPOSURE_SERVICE',
            choices: ['NodePort', 'LoadBalancer'],
            description: 'K8s Service to be used to expose RGW Service to outside cluster.'
        )
        string(name: 'SYSTEM_DRIVE', defaultValue: '/dev/sdb', description: 'Provide appropriate system drive for HW and VM LC cluster', trim: true)
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

        stage ('Deploy CORTX Components') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Deploy CORTX Components', script: '''
                    pushd scripts/performance/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        if [ "$(cat hosts | wc -l)" -eq 2 ]
                        then
                           echo "Current configuration does not support 2 node CORTX cluster deployment. Please try with 1 or more than two nodes."
                           echo "Exiting Jenkins job."
                           exit 1
                        fi
                        export SOLUTION_CONFIG_TYPE=automated
                        export SEAGATE_TOOLS_BRANCH=${SEAGATE_TOOLS_BRANCH}
                        export SEAGATE_TOOLS_REPO=${SEAGATE_TOOLS_REPO}
                        export CORTX_SCRIPTS_BRANCH=${CORTX_SCRIPTS_BRANCH}
                        export CORTX_SCRIPTS_REPO=${CORTX_SCRIPTS_REPO}
                        export CORTX_SERVER_IMAGE=${CORTX_SERVER_IMAGE}
                        export CORTX_DATA_IMAGE=${CORTX_DATA_IMAGE}
                        export CORTX_CONTROL_IMAGE=${CORTX_CONTROL_IMAGE}
                        export DEPLOYMENT_METHOD=${DEPLOYMENT_METHOD}
                        export SNS_CONFIG=${SNS_CONFIG}
                        export DIX_CONFIG=${DIX_CONFIG}
                        export EXTERNAL_EXPOSURE_SERVICE=${EXTERNAL_EXPOSURE_SERVICE}
                        export CONTROL_EXTERNAL_NODEPORT=${CONTROL_EXTERNAL_NODEPORT}
                        export S3_EXTERNAL_HTTP_NODEPORT=${S3_EXTERNAL_HTTP_NODEPORT}
                        export S3_EXTERNAL_HTTPS_NODEPORT=${S3_EXTERNAL_HTTPS_NODEPORT}
                        export NAMESPACE=${NAMESPACE}
                        export SYSTEM_DRIVE=${SYSTEM_DRIVE}
                        ./cortx-perfline.sh --deploy-perfline
                    popd
                '''
            }
        }

        stage ('Perfline_workloads') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Perform Pre-defined perfline workloads', script: '''
                    pushd scripts/performance/
                        export PERFLINE_WORKLOADS_DIR=${PERFLINE_WORKLOADS_DIR}
                        ./cortx-perfline.sh --perfline-workloads
                    popd
                '''
            }
        }
    }
}