#!/usr/bin/env groovy

pipeline {
    agent {
        node {
            label "ceph-build-hw"
        }
    }

    triggers {
        pollSCM '*/10 * * * *'
    }
    
    environment {
        BUILD_LOCATION = "/var/log/rgw/cortx-rgw-build/${BUILD_NUMBER}"
        BUILD_OS = "${os_version}"
        VM_BUILD = false
        CORTX_RGW_OPTIMIZED_BUILD = true
        INSTALL_MOTR = true

        env = "dev"
        component = "cortx-rgw"
        release_tag = "last_successful_prod"
        release_dir = "/mnt/bigstorage/releases/cortx"
        // NFS mount is done manually on agent
        build_upload_dir = "$release_dir/components/github/$branch/$os_version/$env/$component"
    }

    options {
        timeout(time: 300, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
    }

    parameters {  
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re/', description: 'Repository for Cluster Setup scripts.', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts.', trim: true)
        string(name: 'CORTX_RGW_URL', defaultValue: 'https://github.com/Seagate/cortx-rgw', description: 'Repository URL for cortx-rgw build')
        string(name: 'branch', defaultValue: 'main', description: 'Branch for cortx-rgw build')

        choice(
            name: 'os_version',
            choices: ['rockylinux-8.4', 'ubuntu-20.04', 'centos-8'],
            description: 'OS to build binary packages for (*.deb, *.rpm).'
        )
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

        stage ('Build CORTX-RGW Binary Packages') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build Binary Packages', script: """
                pushd solutions/kubernetes/
                    export CEPH_REPO=${CORTX_RGW_URL}
                    export CEPH_BRANCH=${branch}
                    bash ceph-binary-build.sh --ceph-build-env ${BUILD_LOCATION}
                popd
                """
            }
        }

            stage ('Upload') {
                steps {
                    script { build_stage = env.STAGE_NAME }
                    sh label: 'Copy RPMS', script: '''
                        rm -rf $build_upload_dir/$BUILD_NUMBER    
                        mkdir -p $build_upload_dir/$BUILD_NUMBER
                        pushd $BUILD_LOCATION/$BUILD_OS/rpmbuild
                            cp RPMS/*/*.rpm $build_upload_dir/$BUILD_NUMBER
                        popd
                    '''
                    sh label: 'Repo Creation', script: '''pushd $build_upload_dir/$BUILD_NUMBER
                        rpm -qi createrepo || yum install -y createrepo
                        createrepo .
                        popd
                    '''
                }
            }
                            
            stage ('Tag last_successful') {
                when { not { triggeredBy 'UpstreamCause' } }
                steps {
                    script { build_stage = env.STAGE_NAME }
                    sh label: 'Tag last_successful', script: '''pushd $build_upload_dir/
                        test -d $build_upload_dir/last_successful && rm -f last_successful
                        ln -s $build_upload_dir/$BUILD_NUMBER last_successful
                        popd
                    '''
                }
            }

            stage ('Release') {
                when { triggeredBy 'SCMTrigger' }
                steps {
                    script { build_stage = env.STAGE_NAME }
                    script {
                        def releaseBuild = build job: 'Release', propagate: true
                        env.release_build = releaseBuild.number
                        env.release_build_location = "http://cortx-storage.colo.seagate.com/releases/cortx/github/$branch/$os_version/" + releaseBuild.number
                        env.cortx_images = releaseBuild.buildVariables.cortx_all_image + "\n" + releaseBuild.buildVariables.cortx_rgw_image + "\n" + releaseBuild.buildVariables.cortx_data_image + "\n" + releaseBuild.buildVariables.cortx_control_image
                    }
                }
            }

            stage('Update Jira') {
                when { expression { return env.release_build != null } }
                steps {
                    script { build_stage = env.STAGE_NAME }
                    script {
                        def jiraIssues = jiraIssueSelector(issueSelector: [$class: 'DefaultIssueSelector'])
                        jiraIssues.each { issue ->
                            def author =  getAuthor(issue)
                            jiraAddComment(    
                                idOrKey: issue,
                                site: "SEAGATE_JIRA",
                                comment: "{panel:bgColor=#c1c7d0}" +
                                    "h2. ${component} - ${branch} branch build pipeline SUCCESS\n" +
                                    "h3. Build Info:  \n" +
                                            author +
                                                "* Component Build  :  ${BUILD_NUMBER} \n" +
                                            "* Release Build    :  ${release_build}  \n\n  " +
                                    "h3. Artifact Location  :  \n" +
                                        "*  " + "${release_build_location} " + "\n\n" +
                                    "h3. Image Location  :  \n" +
                                        "*  " + "${cortx_images} " + "\n" +
                                    "{panel}",
                                failOnError: false,
                                auditLog: false
                            )
                        }
                    }
                }
            }
        }

    post {
        always {
            script {
                sh label: 'Cleanup Build Location', script: """
                rm -rf ${BUILD_LOCATION}
                """

                echo 'Cleanup Workspace.'
                cleanWs() /* clean up our workspace */

                env.release_build = (env.release_build != null) ? env.release_build : "" 
                env.release_build_location = (env.release_build_location != null) ? env.release_build_location : ""
                env.component = (env.component).toUpperCase()
                env.build_stage = "${build_stage}"

                env.vm_deployment = (env.deployVMURL != null) ? env.deployVMURL : "" 
                if ( env.deployVMStatus != null && env.deployVMStatus != "SUCCESS" && manager.build.result.toString() == "SUCCESS" ) {
                    manager.buildUnstable()
                }

                if ( currentBuild.rawBuild.getCause(hudson.triggers.SCMTrigger$SCMTriggerCause) ) {
                    def toEmail = ""
                    def recipientProvidersClass = [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
                    if ( manager.build.result.toString() == "FAILURE") {
                        toEmail = "CORTX.DevOps.RE@seagate.com"
                    }
                    emailext (
                        body: '''${SCRIPT, template="component-email-dev.template"}''',
                        mimeType: 'text/html',
                        subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                        attachLog: true,
                        to: toEmail,
                        recipientProviders: recipientProvidersClass
                    )
                } else {
                   echo 'Skipping Notification....' 
                }
            }
        }    
    }
}

@NonCPS
def getAuthor(issue) {

    def changeLogSets = currentBuild.rawBuild.changeSets
    def author = ""
    def response = ""
    // Grab build information
    for (int i = 0; i < changeLogSets.size(); i++) {
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {
            def entry = entries[j]
            if ((entry.msg).contains(issue)) {
                author = entry.author
            }
        }
    }
    response = "* Author: " + author + "\n"
    return response
}