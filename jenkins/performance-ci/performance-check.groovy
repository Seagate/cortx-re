/* groovylint-disable DuplicateStringLiteral, LineLength, NestedBlockDepth */
pipeline {
    agent {
        node {
            label 'docker-k8-deployment-node'
        }
    }
    
    options {
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    parameters {
        string(name: 'HARE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Hare', trim: true)
        string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Hare Repository URL', trim: true)
        string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Motr', trim: true)
        string(name: 'MOTR_URL', defaultValue: 'https://github.com/Seagate/cortx-motr.git', description: 'Motr Repository URL', trim: true)
        string(name: 'CORTX_RGW_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX-RGW', trim: true)
        string(name: 'CORTX_RGW_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw', description: 'CORTX-RGW Repository URL', trim: true)
        string(name: 'CORTX_UTILS_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for CORTX Utils', trim: true)
        string(name: 'CORTX_UTILS_URL', defaultValue: 'https://github.com/Seagate/cortx-utils', description: 'CORTX Utils Repository URL', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_TOOLS_BRANCH', defaultValue: 'main', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_TOOLS_REPO', defaultValue: 'Seagate/seagate-tools', description: 'Repository for Cluster Setup scripts', trim: true)

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
            name: 'BUILD_LATEST_CORTX_RGW',
            choices: ['yes', 'no'],
            description: 'Build cortx-rgw from latest code or use last-successful build.'
        )

        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VM details to be used for CORTX cluster setup. First node will be used as Primary', name: 'hosts')
   
    
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'Client Nodes', name: 'client_nodes')
        // Please configure SNS and DIX parameter in Jenkins job configuration.
    }


    environment {
        GITHUB_CRED = credentials('shailesh-github-token')
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

        stage ("Build") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        def cortxbuild = build job: '/GitHub-custom-ci-builds/generic/custom-ci/', wait: true,
                        parameters: [
                                        string(name: 'MOTR_URL', value: "${MOTR_URL}"),
                                        string(name: 'MOTR_BRANCH', value: "${MOTR_BRANCH}"),
                                        string(name: 'MOTR_BUILD_MODE', value: "user-mode"),
                                        string(name: 'ENABLE_MOTR_DTM', value: "no"),
                                        string(name: 'CORTX_RGW_URL', value: "${CORTX_RGW_URL}"),
                                        string(name: 'CORTX_RGW_BRANCH', value: "${CORTX_RGW_BRANCH}"),
                                        string(name: 'HARE_URL', value: "${HARE_URL}"),
                                        string(name: 'HARE_BRANCH', value: "${HARE_BRANCH}"),
                                        string(name: 'CORTX_UTILS_BRANCH', value: "${CORTX_UTILS_BRANCH}"),
                                        string(name: 'CORTX_UTILS_URL', value: "${CORTX_UTILS_URL}"),
                                        string(name: 'THIRD_PARTY_PYTHON_VERSION', value: "${THIRD_PARTY_PYTHON_VERSION}"),
                                        string(name: 'THIRD_PARTY_RPM_VERSION', value: "${THIRD_PARTY_RPM_VERSION}"),
                                        string(name: 'BUILD_LATEST_CORTX_RGW', value: "${BUILD_LATEST_CORTX_RGW}"),
                                        string(name: 'BUILD_MANAGEMENT_PATH_COMPONENTS', value: "no")
                                    ]
                    env.cortxbuild_build_id = cortxbuild.id
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build"
                    }
                }
            }
        }


        stage ("Deploy CORTX Cluster") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def cortxCluster = build job: '/Cortx-Automation/RGW/setup-cortx-rgw-cluster', wait: true,
                    parameters: [
                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                        string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                        string(name: 'CORTX_SERVER_IMAGE', value: "cortx-docker.colo.seagate.com/seagate/cortx-rgw:2.0.0-${env.cortxbuild_build_id}-custom-ci"),
                        string(name: 'CORTX_DATA_IMAGE', value: "cortx-docker.colo.seagate.com/seagate/cortx-data:2.0.0-${env.cortxbuild_build_id}-custom-ci"),
                        string(name: 'CORTX_CONTROL_IMAGE', value: "cortx-docker.colo.seagate.com/seagate/cortx-control:2.0.0-${env.cortxbuild_build_id}-custom-ci"),
                        string(name: 'DEPLOYMENT_METHOD', value: "standard"),
                        text(name: 'hosts', value: "${hosts}"),
                        string(name: 'EXTERNAL_EXPOSURE_SERVICE', value: "NodePort"),
                        string(name: 'SNS_CONFIG', value: "${SNS_CONFIG}"),
                        string(name: 'DIX_CONFIG', value: "${DIX_CONFIG}")
                    ]
                    env.cortxcluster_build_url = cortxCluster.absoluteUrl
                    env.cortxCluster_status = cortxCluster.currentResult
                }
            }
        }

        stage ('Execute performace sanity') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Execute performace sanity', script: '''
                    pushd scripts/performance
                        echo $hosts | tr ' ' '\n' | head -1 > primary_nodes
                        echo $client_nodes | tr ' ' '\n' > client_nodes
                        export GITHUB_TOKEN=${GITHUB_CRED}
                        export CORTX_TOOLS_REPO=${CORTX_TOOLS_REPO}
                        export CORTX_TOOLS_BRANCH=${CORTX_TOOLS_BRANCH}
                        ./run-performace-tests.sh
                    popd
                '''
            }
        }
    }
}