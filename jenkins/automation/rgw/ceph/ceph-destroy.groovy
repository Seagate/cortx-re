pipeline {
    agent {
        node {
            label 'docker-k8-deployment-node'
        }
    }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    parameters {
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts.', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts.', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VMs to destroy Ceph deployment.', name: 'hosts')

        choice(
            name: 'DEPLOYMENT_TYPE',
            choices: ['VM_DEPLOYMENT', 'DOCKER_DEPLOYMENT'],
            description: 'Type of Ceph deployment to destroy.'
        )
    }    

    stages {
        stage ('Checkout Script') {
            steps { 
                cleanWs()            
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])                
                }
            }
        }

        stage ('Destroy Ceph VM Deployment') {
            when { expression { params.DEPLOYMENT_TYPE == 'VM_DEPLOYMENT' } }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Destroy Ceph VM Cluster', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        bash ceph-deploy.sh --destroy-cluster-vm
                    popd
                '''
            }
        }

        stage ('Destroy Ceph Docker Deployment') {
            when { expression { params.DEPLOYMENT_TYPE == 'DOCKER_DEPLOYMENT' } }
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Destroy Ceph Docker Cluster', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        bash ceph-deploy.sh --destroy-cluster-docker
                    popd
                '''
            }
        }

    }

    post {
        always {
            cleanWs()
            script {
                env.build_stage = "${build_stage}"

                def toEmail = ""
                def recipientProvidersClass = [[$class: 'DevelopersRecipientProvider']]
                if ( manager.build.result.toString() == "FAILURE" ) {
                    toEmail = "CORTX.DevOps.RE@seagate.com"
                    recipientProvidersClass = [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
                }

                emailext (
                    body: '''${SCRIPT, template="cluster-setup-email.template"}''',
                    mimeType: 'text/html',
                    subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                    attachLog: true,
                    to: toEmail,
                    recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}