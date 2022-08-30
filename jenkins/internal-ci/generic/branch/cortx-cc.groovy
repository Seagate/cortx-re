#!/usr/bin/env groovy
// Please configure branch and os_version as string parameters in Jenkins configuration manually.
pipeline {
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }

    // triggers {
    //     pollSCM '*/5 * * * *'
    // }
    
    environment {
        version = "2.0.0"
        env = "dev"
        component = "cortx-cc"
        release_dir = "/mnt/bigstorage/releases/cortx"
        release_tag = "last_successful_prod"
        build_upload_dir = "$release_dir/components/github/$branch/$os_version/$env/$component"
    }
    
    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        disableConcurrentBuilds()  
    }

    stages {
        stage('Checkout cortx-cc') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('cortx-cc') {
                    checkout([$class: 'GitSCM', branches: [[name: "${branch}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', depth: 0, noTags: false, reference: '', shallow: false, timeout: 15], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false, timeout: 15]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', refspec: '+refs/heads/main:refs/remotes/origin/main', url: "https://github.com/Seagate/cortx-cc"]]])
                }
            }
        }
    
        stage('Install Dependencies') {
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Install cortx-cc pre-requisites', script: '''
                    yum -y install python3 python3-devel facter yum-utils
                    yum-config-manager --nogpgcheck --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/rockylinux/rockylinux-8.4-$version-latest/
                    yum -y install consul-1.9.1
                '''

                sh label: 'Install cortx-prereq', script: '''
                    yum erase python36-PyYAML -y
                    yum install -y gcc python3 python3-pip python3-devel python3-setuptools openssl-devel libffi-devel python3-dbus
                    pip3 install  --no-cache-dir --trusted-host cortx-storage.colo.seagate.com -i http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/python-deps/python-packages-$version-latest/ -r https://raw.githubusercontent.com/Seagate/cortx-utils/${branch}/py-utils/python_requirements.txt -r https://raw.githubusercontent.com/Seagate/cortx-utils/${branch}/py-utils/python_requirements.ext.txt
                    rm -rf /etc/pip.conf
                '''

                sh label: 'Install Dependencies', script: '''
                    set +x
                    yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/main/$os_version/$release_tag/cortx_iso/
                    yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*
                    yum clean all;rm -rf /var/cache/yum
                    yum install cortx-py-utils cortx-motr{,-devel} -y --nogpgcheck
                '''
            }
        }

        stage('Build') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build', returnStatus: true, script: '''
                    set -xe
                    pushd $component
                    echo "Executing build script"
                    export build_number=${BUILD_ID}
                    make rpm
                    popd
                '''    
            }
        }

        stage ('Upload') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    mkdir -p $build_upload_dir/$BUILD_NUMBER
                    cp /root/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir/$BUILD_NUMBER
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
                    test -L $build_upload_dir/last_successful && rm -f last_successful
                    ln -s $build_upload_dir/$BUILD_NUMBER last_successful
                    popd
                '''
            }
        }

        // stage ("Release") {
        //     when { not { triggeredBy 'UpstreamCause' } }
        //     steps {
        //         script { build_stage = env.STAGE_NAME }
        //         script {
        //             def releaseBuild = build job: 'Release', propagate: true
        //             env.release_build = releaseBuild.number
        //             env.release_build_location = "http://cortx-storage.colo.seagate.com/releases/cortx/github/$branch/$os_version/${env.release_build}"
        //             env.cortx_images = releaseBuild.buildVariables.cortx_all_image + "\n" + releaseBuild.buildVariables.cortx_rgw_image + "\n" + releaseBuild.buildVariables.cortx_data_image + "\n" + releaseBuild.buildVariables.cortx_control_image
        //         }
        //     }
        // }
        // stage('Update Jira') {
        //     when { expression { return env.release_build != null } }
        //     steps {
        //             script { build_stage = env.STAGE_NAME }
        //             script {
        //                 def jiraIssues = jiraIssueSelector(issueSelector: [$class: 'DefaultIssueSelector'])
        //                 jiraIssues.each { issue ->
        //                     def author =  getAuthor(issue)
        //                     jiraAddComment(
        //                         idOrKey: issue,
        //                         site: "SEAGATE_JIRA",
        //                         comment: "{panel:bgColor=#c1c7d0}" +
        //                             "h2. ${component} - ${branch} branch build pipeline SUCCESS\n" +
        //                             "h3. Build Info:  \n" +
        //                                 author +
        //                                     "* Component Build  :  ${BUILD_NUMBER} \n" +
        //                                     "* Release Build    :  ${release_build}  \n\n  " +
        //                             "h3. Artifact Location  :  \n" +
        //                                 "*  " + "${release_build_location} " + "\n\n" +
        //                             "h3. Image Location  :  \n" +
        //                                 "*  " + "${cortx_images} " + "\n" +
        //                             "{panel}",
        //                         failOnError: false,
        //                         auditLog: false
        //                     )
        //                 }
        //             }
        //     }
        // }
    }

    post {
        always {
            script {        
                echo 'Cleanup Workspace.'
                deleteDir() /* clean up our workspace */

                // env.release_build = (env.release_build != null) ? env.release_build : "" 
                // env.release_build_location = (env.release_build_location != null) ? env.release_build_location : ""
                // env.component = (env.component).toUpperCase()
                // env.build_stage = "${build_stage}"

                // // VM Deployment 
                // env.vm_deployment = (env.deployVMURL != null) ? env.deployVMURL : "" 
                // if ( env.deployVMStatus != null && env.deployVMStatus != "SUCCESS" && manager.build.result.toString() == "SUCCESS" ) {
                //     manager.buildUnstable()
                // }

                // def toEmail = ""
                // def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                // if ( manager.build.result.toString() == "FAILURE" ) {
                //     toEmail = "CORTX.DevOps.RE@seagate.com"
                //     recipientProvidersClass = [[$class: 'DevelopersRecipientProvider'],[$class: 'RequesterRecipientProvider']]
                // }

                // emailext (
                //     body: '''${SCRIPT, template="component-email-dev.template"}''',
                //     mimeType: 'text/html',
                //     subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                //     attachLog: true,
                //     to: toEmail,
                //     recipientProviders: recipientProvidersClass
                // )
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