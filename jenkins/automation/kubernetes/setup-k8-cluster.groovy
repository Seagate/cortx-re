pipeline {
    agent {
        node {
            label 'k8-executor'
        }
    }
    
    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }


    parameters {

        string(name: 'CORTX_RE_BRANCH', defaultValue: 'kubernetes', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/shailesh-vaidya/cortx-re', description: 'Repository for Cluster Setup scripts', trim: true)
        text(defaultValue: '''hostname=<hostname>,user=<user>,pass=<password>''', description: 'VM details to be used for K8 cluster setup. First node will be used as Master', name: 'hosts')
    }    

    stages {

        stage('Checkout Script') {
            steps {             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])                
                }
            }
        }


        stage ('Setup Cluster') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Tag last_successful', script: '''
                    pushd solutions/kubernetes/
                        echo $hosts | tr ' ' '\n' > hosts
                        cat hosts
                        ./cluster-setup.sh
                    popd
                '''
            }
        }
    }

    post {
        always {
            cleanWs()
            script {
                env.docker_image_location = "https://github.com/Seagate/cortx-re/pkgs/container/cortx-all"
                env.image = sh( script: "docker images --format='{{.Repository}}:{{.Tag}}' | head -1", returnStdout: true).trim()
                env.build_stage = "${build_stage}"
                def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                mailRecipients = "shailesh.vaidya@seagate.com"
                emailext ( 
                    body: '''${SCRIPT, template="cluster-setup-email.template"}''',
                    mimeType: 'text/html',
                    subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                    attachLog: true,
                    to: "${mailRecipients}",
                    recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}