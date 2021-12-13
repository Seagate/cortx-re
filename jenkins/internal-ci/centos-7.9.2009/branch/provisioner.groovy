#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }
    
    triggers {
        pollSCM '*/5 * * * *'
    }
    
    environment {
        version = "2.0.0"
        env = "dev"
        component = "provisioner"
        release_dir = "/mnt/bigstorage/releases/cortx"
        build_upload_dir = "$release_dir/components/github/$branch/$os_version/$env/$component"
    }

    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        disableConcurrentBuilds()   
    }
    
    stages {

        stage('Checkout') {
            steps {
                script { build_stage = env.STAGE_NAME }
                    checkout([$class: 'GitSCM', branches: [[name: "${branch}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-prvsnr.git']]])
            }
        }

        stage('Build') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh encoding: 'utf-8', label: 'Provisioner RPMS', returnStdout: true, script: """
                    sh ./devops/rpms/buildrpm.sh -g \$(git rev-parse --short HEAD) -e $version -b ${BUILD_NUMBER}
                """
                sh encoding: 'utf-8', label: 'Provisioner CLI RPMS', returnStdout: true, script: """
                    sh ./cli/buildrpm.sh -g \$(git rev-parse --short HEAD) -e $version -b ${BUILD_NUMBER}
                """
                sh encoding: 'UTF-8', label: 'cortx-setup', script: """
                if [ -f "./devops/rpms/node_cli/node_cli_buildrpm.sh" ]; then
                    sh ./devops/rpms/node_cli/node_cli_buildrpm.sh -g \$(git rev-parse --short HEAD) -e $version -b ${BUILD_NUMBER}
                else
                    echo "node_cli package creation is not implemented"
                fi
                """
                
                sh encoding: 'UTF-8', label: 'api', script: '''
                    bash ./devops/rpms/api/build_python_api.sh -vv --out-dir /root/rpmbuild/RPMS/x86_64/ --pkg-ver ${BUILD_NUMBER}_git$(git rev-parse --short HEAD)
                '''

                sh encoding: 'UTF-8', label: 'cortx-setup', script: '''
                    bash ./devops/rpms/lr-cli/build_python_cortx_setup.sh -vv --out-dir /root/rpmbuild/RPMS/x86_64/ --pkg-ver ${BUILD_NUMBER}_git$(git rev-parse --short HEAD)
                '''

                sh encoding: 'UTF-8', label: 'cortx-provisioner', script: '''
                if [ -f "./jenkins/build.sh" ]; then
                    bash ./jenkins/build.sh -v 2.0.0 -b ${BUILD_NUMBER}
                else
                    echo "cortx-provisioner package creation is not implemented"
                fi
                '''
            }
        }

        stage ('Upload') {
            steps {
            sh encoding: 'utf-8', label: 'Provisioner RPMS', returnStdout: true, script: """
                mkdir -p $build_upload_dir/$BUILD_NUMBER
                cp /root/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir/$BUILD_NUMBER
                rpm -qi createrepo || yum install -y createrepo
                createrepo -v .
            """
            }
        }
            
        stage ('Tag last_successful') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh encoding: 'utf-8', label: 'Provisioner RPMS', returnStdout: true, script: """
                    pushd $build_upload_dir/
                    test -d $build_upload_dir/last_successful && rm -f last_successful
                    ln -s $build_upload_dir/$BUILD_NUMBER last_successful
                    popd
                """
            }
        }

        stage ("Release") {
            when { triggeredBy 'SCMTrigger' }
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def releaseBuild = build job: 'Release', propagate: true
                     env.release_build = releaseBuild.number
                    env.release_build_location="http://cortx-storage.colo.seagate.com/releases/cortx/github/$branch/$os_version/${env.release_build}"
                }
            }
        }
    }

    post {
        always {                
            script {
                env.release_build = (env.release_build != null) ? env.release_build : "" 
                env.release_build_location = (env.release_build_location != null) ? env.release_build_location : ""
                env.component = (env.component).capitalize()
                env.build_stage = "${build_stage}"

                env.vm_deployment = (env.deployVMURL != null) ? env.deployVMURL : "" 
                if ( env.deployVMStatus != null && env.deployVMStatus != "SUCCESS" && manager.build.result.toString() == "SUCCESS" ) {
                    manager.buildUnstable()
                }

                def toEmail = ""
                def recipientProvidersClass = [[$class: 'DevelopersRecipientProvider']]
                if( manager.build.result.toString() == "FAILURE" ) {
                    toEmail = "shailesh.vaidya@seagate.com"
                    recipientProvidersClass = [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
                }

                emailext (
                    body: '''${SCRIPT, template="component-email-dev.template"}''',
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
