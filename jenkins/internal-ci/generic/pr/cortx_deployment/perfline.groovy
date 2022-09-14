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

    parameters {
	    string(name: 'SEAGATE_TOOLS_REPO', defaultValue: 'https://github.com/Seagate/seagate-tools.git', description: 'Repo for PERFLINE')
        string(name: 'SEAGATE_TOOLS_BRANCH', defaultValue: 'main', description: 'Branch for PERFLINE')
        choice(name: 'DEPLOY_BUILD_ON_NODES', choices: ["Both", "1node", "3node" ], description: '''<pre>If you select Both then build will be deploy on 1 node as well as 3 node. If you select 1 node then build will be deploy on 1 node only. If you select 3 node then build will be deploy on 3 node only.
</pre>''')
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repo for cortx-re')
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch for cortx-re')

        string(name: 'CORTX_SERVER_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-rgw:2.0.0-latest', description: 'CORTX-SERVER image', trim: true)
        string(name: 'CORTX_DATA_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-data:2.0.0-latest', description: 'CORTX-DATA image', trim: true)
        string(name: 'CORTX_CONTROL_IMAGE', defaultValue: 'ghcr.io/seagate/cortx-control:2.0.0-latest', description: 'CORTX-CONTROL image', trim: true)
        choice(
            name: 'DEPLOYMENT_METHOD',
            choices: ['data-only', 'standard'],
            description: 'Method to deploy required CORTX service. standard method will deploy all CORTX services'
        )
        string(name: 'CONTROL_EXTERNAL_NODEPORT', defaultValue: '31169', description: 'Port to be used for control service.', trim: true)
        string(name: 'S3_EXTERNAL_HTTP_NODEPORT', defaultValue: '30080', description: 'HTTP Port to be used for IO service.', trim: true)
        string(name: 'S3_EXTERNAL_HTTPS_NODEPORT', defaultValue: '30443', description: 'HTTPS to be used for IO service.', trim: true)
        string(name: 'NAMESPACE', defaultValue: 'cortx', description: 'kubernetes namespace to be used for CORTX deployment.', trim: true)
        choice(
            name: 'EXTERNAL_EXPOSURE_SERVICE',
            choices: ['NodePort', 'LoadBalancer'],
            description: 'K8s Service to be used to expose RGW Service to outside cluster.'
        )
        string(name: 'PERFLINE_WORKLOADS_DIR', defaultValue: "/root/perfline/wrapper/workload/jenkins/mini_workload", description: 'specify the location of your workload directory', trim: true)
        string(name: 'CORTX_SCRIPTS_BRANCH', defaultValue: "v0.10.0", description: 'Stable service framework version', trim: true)
        choice (
            choices: ['DEBUG', 'ALL'],
            description: 'Email Notification Recipients ',
            name: 'EMAIL_RECIPIENTS'
        )
        string(name: 'SYSTEM_DRIVE', defaultValue: '/dev/sdb', description: 'Provide appropriate system drive for HW and VM LC cluster', trim: true)

        // Please configure singlenode_host, threenode_hosts, SNS and DIX parameter in Jenkins job configuration manually.
	}

    environment {
        GITHUB_CRED = credentials('shailesh-github-token')
        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        SEAGATE_TOOLS_REPO = "${ghprbGhRepository != null ? GPR_REPO : SEAGATE_TOOLS_REPO}"
        SEAGATE_TOOLS_BRANCH = "${sha1 != null ? sha1 : SEAGATE_TOOLS_BRANCH}"
        TOOLS_GPR_REFSEPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        TOOLS_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        TOOLS_PR_REFSEPEC = "${ghprbPullId != null ? TOOLS_GPR_REFSEPEC : TOOLS_BRANCH_REFSEPEC}"
    }

    stages {

        stage ('Perform pre-defined perfline workloads') {
            parallel {
                stage ("On Single Node") {
                    when { expression { params.DEPLOY_BUILD_ON_NODES ==~ /Both|1node/ } }
                        steps {
                            script { build_stage = env.STAGE_NAME }
                            script {
                                def cortxCluster = build job: "/Motr/cortx-perfline/", wait: true,
                                parameters: [
                                    string(name: 'SEAGATE_TOOLS_BRANCH', value: "${SEAGATE_TOOLS_BRANCH}"),
                                    string(name: 'SEAGATE_TOOLS_REPO', value: "${SEAGATE_TOOLS_REPO}"),
                                    string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                                    string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                                    string(name: 'CORTX_SCRIPTS_BRANCH', value: "${CORTX_SCRIPTS_BRANCH}"),
                                    string(name: 'CORTX_SERVER_IMAGE', value: "${CORTX_SERVER_IMAGE}"),
                                    string(name: 'CORTX_DATA_IMAGE', value: "${CORTX_DATA_IMAGE}"),
                                    string(name: 'CORTX_CONTROL_IMAGE', value: "${CORTX_CONTROL_IMAGE}"),
                                    string(name: 'SNS_CONFIG', value: "${SNS_CONFIG}"),
                                    string(name: 'DIX_CONFIG', value: "${DIX_CONFIG}"),
                                    string(name: 'CONTROL_EXTERNAL_NODEPORT', value: "${CONTROL_EXTERNAL_NODEPORT}"),
                                    string(name: 'S3_EXTERNAL_HTTP_NODEPORT', value: "${S3_EXTERNAL_HTTP_NODEPORT}"),
                                    string(name: 'S3_EXTERNAL_HTTPS_NODEPORT', value: "${S3_EXTERNAL_HTTPS_NODEPORT}"),
                                    string(name: 'NAMESPACE', value: "${NAMESPACE}"),
                                    string(name: 'PERFLINE_WORKLOADS_DIR', value: "${PERFLINE_WORKLOADS_DIR}"),
                                    string(name: 'SYSTEM_DRIVE', value: "${SYSTEM_DRIVE}"),
                                    string(name: 'hosts', value: "${singlenode_host}"),
                                    string(name: 'EMAIL_RECIPIENTS', value: "DEBUG")
                                ]
                                copyArtifacts filter: 'artifacts/perfline*', fingerprintArtifacts: true, flatten: true, optional: true, projectName: '/Motr/cortx-perfline/', selector: lastCompleted(), target: ''
                            }
                        }
                }
                stage ("On 3 node setup") {
                    when { expression { params.DEPLOY_BUILD_ON_NODES ==~ /Both|3node/ } }
                    steps {
                        script { build_stage = env.STAGE_NAME }
                        script {
                            def cortxCluster = build job: "/Motr/cortx-perfline/", wait: true,
                            parameters: [
                                string(name: 'SEAGATE_TOOLS_BRANCH', value: "${SEAGATE_TOOLS_BRANCH}"),
                                string(name: 'SEAGATE_TOOLS_REPO', value: "${SEAGATE_TOOLS_REPO}"),
                                string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                                string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}"),
                                string(name: 'CORTX_SCRIPTS_BRANCH', value: "${CORTX_SCRIPTS_BRANCH}"),
                                string(name: 'CORTX_SERVER_IMAGE', value: "${CORTX_SERVER_IMAGE}"),
                                string(name: 'CORTX_DATA_IMAGE', value: "${CORTX_DATA_IMAGE}"),
                                string(name: 'CORTX_CONTROL_IMAGE', value: "${CORTX_CONTROL_IMAGE}"),
                                string(name: 'SNS_CONFIG', value: "${SNS_CONFIG}"),
                                string(name: 'DIX_CONFIG', value: "${DIX_CONFIG}"),
                                string(name: 'CONTROL_EXTERNAL_NODEPORT', value: "${CONTROL_EXTERNAL_NODEPORT}"),
                                string(name: 'S3_EXTERNAL_HTTP_NODEPORT', value: "${S3_EXTERNAL_HTTP_NODEPORT}"),
                                string(name: 'S3_EXTERNAL_HTTPS_NODEPORT', value: "${S3_EXTERNAL_HTTPS_NODEPORT}"),
                                string(name: 'NAMESPACE', value: "${NAMESPACE}"),
                                string(name: 'PERFLINE_WORKLOADS_DIR', value: "${PERFLINE_WORKLOADS_DIR}"),
                                string(name: 'SYSTEM_DRIVE', value: "${SYSTEM_DRIVE}"),
                                string(name: 'hosts', value: "${threenode_hosts}"),
                                string(name: 'EMAIL_RECIPIENTS', value: "DEBUG")
                            ]
                            copyArtifacts filter: 'artifacts/perfline*', fingerprintArtifacts: true, flatten: true, optional: true, projectName: '/Motr/cortx-perfline/', selector: lastCompleted(), target: ''
                        }
                    }
                }
            }
        }
    }
    post {
        failure {
            script {
                manager.addShortText("${build_stage} Failed")
            }
        }
    }

}
