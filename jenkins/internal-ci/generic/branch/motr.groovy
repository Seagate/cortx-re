#!/usr/bin/env groovy
// Please configure branch and os_version as string parameters in Jenkins configuration manually.
pipeline {
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }

    triggers {
        pollSCM '*/5 * * * *'
    }
    
    parameters {
        choice(
            choices: ['libfabric' , 'lustre'],
            description: '',
            name: 'TRANSPORT')
    }
        
    environment {
        version = "2.0.0"    
        env = "dev"
        component = "motr"
        release_dir = "/mnt/bigstorage/releases/cortx"
        build_upload_dir = "$release_dir/components/github/$branch/$os_version/$env/$component"

        // Dependent component job build
        build_upload_dir_s3_dev = "$release_dir/components/github/$branch/$os_version/$env/s3server"
        build_upload_dir_hare = "$release_dir/components/github/$branch/$os_version/$env/hare"
    }
    
    options {
        timeout(time: 300, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()  
    }

    stages {
        stage('Checkout') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('motr') {
                    checkout([$class: 'GitSCM', branches: [[name: "*/${branch}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-motr']]])
                }
            }
        }
        
    stage('Check TRANSPORT module') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('motr') {
                    sh label: '', script: '''
                        if [[ "$TRANSPORT" == "libfabric" ]]; then
                            echo "We are using default 'libfabric' module/package"
                        else
                            sed -i '/libfabric/d' cortx-motr.spec.in
                            echo "Removed libfabric from spec file as we are going to use $TRANSPORT"
                         fi
                    '''
                }
            }
        }

        stage('Install Dependencies') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('motr') {
                    
                    sh label: '', script: '''
                    yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/rockylinux/rockylinux-8.4-2.0.0-latest/
                    yum --nogpgcheck -y --disablerepo="EOS_Rocky_8_OS_x86_64_Rocky_8" install libfabric-1.11.2 libfabric-devel-1.11.2
                    '''

                    sh label: '', script: '''
                        export build_number=${BUILD_ID}
                        cp cortx-motr.spec.in cortx-motr.spec
                        sed -i "/BuildRequires.*kernel*/d" cortx-motr.spec
                        sed -i "/BuildRequires.*%{lustre_devel}/d" cortx-motr.spec
                        sed -i 's/@BUILD_DEPEND_LIBFAB@//g' cortx-motr.spec
                        sed -i 's/@.*@/111/g' cortx-motr.spec
                        yum-builddep -y --nogpgcheck cortx-motr.spec
                    ''' 
                }
            }
        }

        stage('Build') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('motr') {    
                    sh label: '', script: '''
                        rm -rf /root/rpmbuild/RPMS/x86_64/*.rpm
                        ./autogen.sh
                        ./configure --with-user-mode-only
                        export build_number=${BUILD_ID}
                        make rpms
                    '''
                }    
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
        
        stage ('Set Current Build') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Tag last_successful', script: '''
                    pushd $build_upload_dir/
                    test -d $build_upload_dir/current_build && rm -f current_build
                    ln -s $build_upload_dir/$BUILD_NUMBER current_build
                    popd
                '''
            }
        }
        
        stage ("build Hare") {
            steps {
                    script { build_stage = env.STAGE_NAME }
                    script {
                        try {
                            def hareBuild = build job: 'hare', wait: true,
                            parameters: [
                                string(name: 'branch', value: "${branch}")
                            ]
                            env.HARE_BUILD_NUMBER = hareBuild.number
                        }catch (err) {
                            build_stage = env.STAGE_NAME
                            error "Failed to Build Hare"
                        }
                    }
                }
            }


        stage ('Tag last_successful') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Tag last_successful', script: '''pushd $build_upload_dir/
                    test -d $build_upload_dir/last_successful && rm -f last_successful
                    ln -s $build_upload_dir/$BUILD_NUMBER last_successful
                    popd
                '''
                sh label: 'Tag last_successful for dep component', script: '''
                    # Hare Build 
                    test -L $build_upload_dir_hare/last_successful && rm -f $build_upload_dir_hare/last_successful
                    ln -s $build_upload_dir_hare/$HARE_BUILD_NUMBER $build_upload_dir_hare/last_successful

                    #test -L /mnt/bigstorage/releases/cortx/components/github/rgw/rockylinux-8.4/dev/hare/last_successful && rm -rf /mnt/bigstorage/releases/cortx/components/github/rgw/rockylinux-8.4/dev/hare/last_successful
                    #ln -s /mnt/bigstorage/releases/cortx/components/github/rgw/rockylinux-8.4/dev/hare/$HARE_BUILD_NUMBER /mnt/bigstorage/releases/cortx/components/github/rgw/rockylinux-8.4/dev/hare/last_successful

                    # S3Server Build
                    #test -L $build_upload_dir_s3_dev/last_successful && rm -f $build_upload_dir_s3_dev/last_successful
                    #ln -s $build_upload_dir_s3_dev/$S3_BUILD_NUMBER $build_upload_dir_s3_dev/last_successful
                '''
            }
        }

        stage ("Release") {
            when { triggeredBy 'SCMTrigger' }
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def releaseBuild = build job: 'Release', propagate: true
                    env.release_build = releaseBuild.number
                    env.release_build_location = "http://cortx-storage.colo.seagate.com/releases/cortx/github/$branch/$os_version/${env.release_build}"
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

                        //def jiraFileds = jiraGetIssue idOrKey: issue, site: "SEAGATE_JIRA", failOnError: false
                        //if(jiraFileds.data != null){
                        //def labels_data =  jiraFileds.data.fields.labels + "cortx_stable_b${release_build}"
                        //jiraEditIssue idOrKey: issue, issue: [fields: [ labels: labels_data ]], site: "SEAGATE_JIRA", failOnError: false
                        // }
                        }
                    }
                }    
        }
    }

    post {
        always {
            script {
                echo 'Cleanup Workspace.'
                deleteDir() /* clean up our workspace */

                env.release_build = (env.release_build != null) ? env.release_build : "" 
                env.release_build_location = (env.release_build_location != null) ? env.release_build_location : ""
                env.component = (env.component).toUpperCase()
                env.build_stage = "${build_stage}"

                env.vm_deployment = (env.deployVMURL != null) ? env.deployVMURL : "" 
                if ( env.deployVMStatus != null && env.deployVMStatus != "SUCCESS" && manager.build.result.toString() == "SUCCESS" ) {
                    manager.buildUnstable()
                }

                def toEmail = ""
                def recipientProvidersClass = [[$class: 'DevelopersRecipientProvider']]
                if ( manager.build.result.toString() == "FAILURE" ) {
                    toEmail = "CORTX.DevOps.RE@seagate.com"
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
