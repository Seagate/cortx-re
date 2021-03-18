#!/usr/bin/env groovy
// CLEANUP REQUIRED
pipeline { 

    agent {
        node {
            // Run deployment on mini_provisioner nodes (vm deployment nodes)
            label "mini_provisioner_sspl"
        }
    }

    options { 
        skipDefaultCheckout()
        timeout(time: 180, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')  
    }

    parameters {  
	    string(name: 'SSPL_URL', defaultValue: 'https://github.com/Seagate/cortx-monitor', description: 'Repo for SSPL')
        string(name: 'SSPL_BRANCH', defaultValue: 'main', description: 'Branch for SSPL')  
	}

    environment {

        // S3Server Repo Info

        GPR_REPO = "https://github.com/${ghprbGhRepository}"
        SSPL_URL = "${ghprbGhRepository != null ? GPR_REPO : SSPL_URL}"
        SSPL_BRANCH = "${sha1 != null ? sha1 : SSPL_BRANCH}"

        SSPL_GPR_REFSEPEC = "+refs/pull/${ghprbPullId}/*:refs/remotes/origin/pr/${ghprbPullId}/*"
        SSPL_BRANCH_REFSEPEC = "+refs/heads/*:refs/remotes/origin/*"
        SSPL_PR_REFSEPEC = "${ghprbPullId != null ? SSPL_GPR_REFSEPEC : SSPL_BRANCH_REFSEPEC}"
        
        ////////////////////////////////// DEPLOYMENT VARS /////////////////////////////////////////////////////

        CORTX_MONITOR_BASE_URL = "https://raw.githubusercontent.com/Seagate/cortx-monitor/main"

        STAGE_DEPLOY = "yes"
    }

    stages {

        // Build s3server fromm PR source code
        stage('Build') {
            steps {
				script { build_stage = env.STAGE_NAME }
                 
                dir("cortx-monitor") {

                    checkout([$class: 'GitSCM', branches: [[name: "${SSPL_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false], [$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${SSPL_URL}",  name: 'origin', refspec: "${SSPL_PR_REFSEPEC}"]]])

                    sh label: '', script: '''
                        rm -rf /root/SSPL_RPMS
                        rm -rf /root/*.html
                        yum install -y autoconf automake libtool check-devel doxygen rpm-build gcc openssl-devel graphviz python-pep8 python36-devel libffi-devel
                        sed -i 's/gpgcheck=1/gpgcheck=0/' /etc/yum.conf
                    '''
                    sh label: 'Build', script: '''
                        VERSION=$(cat VERSION)
                        export build_number=${BUILD_ID}
                        #Execute build script
                        echo "Executing build script"
                        echo "VERSION:$VERSION"
                        ./jenkins/build.sh -v $VERSION -l DEBUG
                    '''	

                    sh label: 'Copy RPMS', script: '''
                        rm -rf /root/SSPL_RPMS
                        mkdir -p /root/SSPL_RPMS
                        cp /root/rpmbuild/RPMS/x86_64/*.rpm /root/SSPL_RPMS
                        cp /root/rpmbuild/RPMS/noarch/*.rpm /root/SSPL_RPMS
                    '''
                }
            }
        }

        // Deploy sspl mini provisioner 
        // 1. Install build tools
        // 2. Download deploy wrapper script
        // 3. Cleanup old installation
        // 4. Setup yum repo for dependency installation
        // 5. Deploy SSPL
        // 6. Validate Deployment
        // Ref - https://github.com/Seagate/cortx-monitor/wiki/LR2:-SSPL-Self-Provisioning
        stage('Deploy') {
            when { expression { env.STAGE_DEPLOY == "yes" } }
            steps {
                script { build_stage = env.STAGE_NAME }
                script {

                    // Cleanup Workspace
                    cleanWs()

                    manager.addHtmlBadge("&emsp;<b>Deployment Host :</b><a href='${JENKINS_URL}/computer/${env.NODE_NAME}'> ${env.NODE_NAME}</a>&emsp;")

                    // Run Deployment
                    catchError {
                        
                        dir('cortx-re') {
                            checkout([$class: 'GitSCM', branches: [[name: '*/mini-provisioner-dev']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 1, honorRefspec: true, noTags: true, reference: '', shallow: true], [$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
                        }

                        dir("cortx-re/scripts/mini_provisioner") {
                            ansiblePlaybook(
                                playbook: 'sspl_deploy.yml',
                                extraVars: [
                                    "CORTX_MONITOR_BASE_URL" : [value: "${CORTX_MONITOR_BASE_URL}", hidden: false]
                                ],
                                extras: '-v',
                                colorized: true
                            )
                        }

                    }

                    sh '''
                        cp /root/*.html . || :
                        cp /root/*.log . || :
                    '''
                    // Collect logs from test node
                    archiveArtifacts artifacts: "*.log, *.html", onlyIfSuccessful: false, allowEmptyArchive: true 

                    // Cleanup Workspace
                    cleanWs()
                }
            }
        }
	}

    post {
        always {
            sh label: 'Remove artifacts', script: '''rm -rf "${DESTINATION_RELEASE_LOCATION}"'''
        }
        failure {
            script {
                manager.addShortText("${build_stage} Failed")
            }  
        }
    }
}	
