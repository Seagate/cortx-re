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
        string(name: 'SYSTEM_DRIVE', defaultValue: '/dev/mapper/mpathp', description: 'Partition to be used for local provisioner', trim: true)

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

        choice (
            choices: ['HW', 'VM'],
            description: 'Target infrasture for CORTX cluster ',
            name: 'infrastructure'
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

        stage ('Build') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        def cortxbuild = build job: '/GitHub-custom-ci-builds/generic/custom-ci/', wait: true,
                        parameters: [
                                        string(name: 'MOTR_URL', value: "${MOTR_URL}"),
                                        string(name: 'MOTR_BRANCH', value: "${MOTR_BRANCH}"),
                                        string(name: 'MOTR_BUILD_MODE', value: 'user-mode'),
                                        string(name: 'ENABLE_MOTR_DTM', value: 'no'),
                                        string(name: 'CORTX_RGW_URL', value: "${CORTX_RGW_URL}"),
                                        string(name: 'CORTX_RGW_BRANCH', value: "${CORTX_RGW_BRANCH}"),
                                        string(name: 'HARE_URL', value: "${HARE_URL}"),
                                        string(name: 'HARE_BRANCH', value: "${HARE_BRANCH}"),
                                        string(name: 'CORTX_UTILS_BRANCH', value: "${CORTX_UTILS_BRANCH}"),
                                        string(name: 'CORTX_UTILS_URL', value: "${CORTX_UTILS_URL}"),
                                        string(name: 'THIRD_PARTY_PYTHON_VERSION', value: "${THIRD_PARTY_PYTHON_VERSION}"),
                                        string(name: 'THIRD_PARTY_RPM_VERSION', value: "${THIRD_PARTY_RPM_VERSION}"),
                                        string(name: 'BUILD_LATEST_CORTX_RGW', value: "${BUILD_LATEST_CORTX_RGW}"),
                                        string(name: 'BUILD_MANAGEMENT_PATH_COMPONENTS', value: 'no')
                                    ]
                    env.cortxbuild_build_id = cortxbuild.id
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error 'Failed to Build'
                    }
                }
            }
        }


        stage ('Define Build Variables') {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    env.allhost = sh( script: '''
                    echo $hosts | tr ' ' '\n' | awk -F["="] '{print $2}'|cut -d',' -f1
                    ''', returnStdout: true).trim()

                    env.master_node = sh( script: '''
                    echo $hosts | tr ' ' '\n' | head -1 | awk -F["="] '{print $2}' | cut -d',' -f1
                    ''', returnStdout: true).trim()

                    env.hostpasswd = sh( script: '''
                    echo $hosts | tr ' ' '\n' | head -1 | awk -F["="] '{print $4}'
                    ''', returnStdout: true).trim()

                    env.numberofnodes = sh( script: '''
                    echo $hosts | tr ' ' '\n' | tail -n +1 | wc -l
                    ''', returnStdout: true).trim()

                    env.primarynodes = sh( script: '''
                    echo $hosts | tr ' ' '\n' | head -1
                    ''', returnStdout: true).trim()
                }
            }
        }

        stage ('Deploy CORTX Cluster on HW') {
             when {
                expression { params.infrastructure == 'HW' }
            }
            steps {
                script { build_stage = env.STAGE_NAME }

                checkout([$class: 'GitSCM', branches: [[name: '$CORTX_RE_BRANCH']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: '$CORTX_RE_REPO']]])

                sh label: 'Deploy CORTX', script:'''
                pushd solutions/kubernetes/
                      curl -l http://cortx-storage.colo.seagate.com/releases/cortx/solution_template/nightly-performance-ci.yml -o solution.yaml
                      export GITHUB_TOKEN=$GITHUB_TOKEN
                      export CORTX_SCRIPTS_BRANCH=${CORTX_SCRIPTS_BRANCH}
                      export CORTX_SCRIPTS_REPO=${CORTX_SCRIPTS_REPO}
                      export CORTX_CONTROL_IMAGE=${CORTX_CONTROL_IMAGE}
                      export CORTX_SERVER_IMAGE=${CORTX_SERVER_IMAGE}
                      export CORTX_DATA_IMAGE=${CORTX_DATA_IMAGE}
                      export SOLUTION_CONFIG_TYPE=manual
                      export DEPLOYMENT_METHOD=${DEPLOYMENT_METHOD}
                      export SYSTEM_DRIVE=${SYSTEM_DRIVE}

                      #Print Host details.
                      echo $hosts | tr ' ' '\n' > hosts
                      cat hosts

                      #Destroy CORTX Cluster
                      ls -ltr
                      ./cortx-deploy.sh --destroy-cluster

                      #Deploy CORTX Cluster
                      ./cortx-deploy.sh --cortx-cluster

                '''

            }
        }

        stage ('Deploy CORTX Cluster on VM') {
             when {
                expression { params.infrastructure == 'VM' }
            }
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
                        string(name: 'DEPLOYMENT_METHOD', value: 'standard'),
                        text(name: 'hosts', value: "${hosts}"),
                        string(name: 'EXTERNAL_EXPOSURE_SERVICE', value: 'NodePort'),
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
                script {
                    def cortxCluster = build job: '/Cortx-Automation/Performance/run-performance-sanity/', wait: true,
                    parameters: [
                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                        string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                        string(name: 'CORTX_TOOLS_BRANCH', value: "${CORTX_TOOLS_BRANCH}"),
                        string(name: 'CORTX_TOOLS_REPO', value: "${CORTX_TOOLS_REPO}"),
                        string(name: 'infrastructure', value: "${infrastructure}"),
                        text(name: 'primary_nodes', value: "${env.primarynodes}"),
                        text(name: 'client_nodes', value: "${client_nodes}")
                    ]
                    copyArtifacts filter: 'artifacts/perf*', fingerprintArtifacts: true, flatten: true, optional: true, projectName: '/Cortx-Automation/Performance/run-performance-sanity/', selector: lastCompleted(), target: ''
                }
            }
        }
    }
}