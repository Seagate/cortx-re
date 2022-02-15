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
        component = "cortx-rgw"
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

        stage('Checkout cortx-rgw') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir ('cortx-rgw') {
                checkout([$class: 'GitSCM', branches: [[name: "${branch}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "https://github.com/Seagate/cortx-rgw"]]])
                }
            }
        }
        
        stage('Install Dependencies') {
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Build', script: '''                    
                #sed -i -e \'s/Library/Production\\/Rocky_8_Content_View/g\' -e  \'/http.*EPEL/s/Rocky_8\\/EPEL/EPEL-8\\/EPEL-8/g\' /etc/yum.repos.d/R8.4.repo

                pushd cortx-rgw
                    ./install-deps.sh
                    ./make-dist
                    mkdir -p /mnt/rgw/$BUILD_NUMBER/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS}
                    tar --strip-components=1 -C /mnt/rgw/$BUILD_NUMBER/SPECS/ --no-anchored -xvjf ceph-*tar.bz2 "ceph.spec"
                    mv ceph*tar.bz2 /mnt/rgw/$BUILD_NUMBER/SOURCES/
                popd
                '''

                sh label: 'Configure yum repositories', script: """
                    set +x
                    yum-config-manager --add-repo=http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/$os_version/$release_tag/cortx_iso/
                    yum-config-manager --save --setopt=cortx-storage*.gpgcheck=1 cortx-storage* && yum-config-manager --save --setopt=cortx-storage*.gpgcheck=0 cortx-storage*
                    yum clean all;rm -rf /var/cache/yum
                    yum install cortx-motr{,-devel} -y
                """
            }
        }

        stage('Build cortx-rgw packages') {
            steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Build', script: '''
                cd /mnt/rgw/$BUILD_NUMBER
                rpmbuild --clean --rmsource --define "_unpackaged_files_terminate_build 0" --define "debug_package %{nil}" --without cmake_verbose_logging --without jaeger --without lttng --without seastar --without kafka_endpoint --without zbd --without cephfs_java --without cephfs_shell --without ocf --without selinux --without ceph_test_package --without make_check --define "_binary_payload w2T16.xzdio" --define "_topdir `pwd`" -ba /mnt/rgw/$BUILD_NUMBER/SPECS/ceph.spec
                '''
            }
        }

        stage ('Upload') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script: '''
                    rm -rf $build_upload_dir/$BUILD_NUMBER     
                    mkdir -p $build_upload_dir/$BUILD_NUMBER
                    cp /root/rpmbuild/RPMS/x86_64/*.rpm $build_upload_dir/$BUILD_NUMBER
                    cp /root/rpmbuild/RPMS/noarch/*.rpm $build_upload_dir/$BUILD_NUMBER
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

        stage ("Release Build") {
            when { expression { false } }
            // when { not { triggeredBy 'UpstreamCause' } }
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def releaseBuild = build job: 'Release', propagate: true
                    env.release_build = releaseBuild.number
                    env.release_build_location="http://cortx-storage.colo.seagate.com/releases/cortx/github/$branch/$os_version/${env.release_build}"
                    env.cortx_all_image = releaseBuild.buildVariables.cortx_all_image
                }
            }
        }
        
        stage('Update Jira') {
            when { expression { false } }
           // when { expression { return env.release_build != null } }
            steps {
                script { build_stage=env.STAGE_NAME }
                    script {
                        def jiraIssues = jiraIssueSelector(issueSelector: [$class: 'DefaultIssueSelector'])
                        jiraIssues.each { issue ->
                            def author =  getAuthor(issue)
                            jiraAddComment(
                                idOrKey: issue,
                                site: "SEAGATE_JIRA",
                                comment: "{panel:bgColor=#c1c7d0}"+
                                    "h2. ${component} - ${branch} branch build pipeline SUCCESS\n"+
                                    "h3. Build Info:  \n"+
                                        author+
                                            "* Component Build  :  ${BUILD_NUMBER} \n"+
                                            "* Release Build    :  ${release_build}  \n\n  "+
                                    "h3. Artifact Location  :  \n"+
                                        "*  "+"${release_build_location} "+"\n"+
                                        "{panel}",
                                failOnError: false,
                                auditLog: false
                            )
                            //def jiraFileds = jiraGetIssue idOrKey: issue, site: "SEAGATE_JIRA", failOnError: false
                            //if(jiraFileds.data != null){
                            //def labels_data =  jiraFileds.data.fields.labels + "cortx_stable_b${release_build}"
                            //jiraEditIssue idOrKey: issue, issue: [fields: [ labels: labels_data ]], site: "SEAGATE_JIRA", failOnError: false
                            //}
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

                if( currentBuild.rawBuild.getCause(hudson.triggers.SCMTrigger$SCMTriggerCause) ) {
                    def toEmail = "shailesh.vaidya@seagate.com"
                    def recipientProvidersClass = [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
                    if( manager.build.result.toString() == "FAILURE") {
                        toEmail = "shailesh.vaidya@seagate.com"
                    }
                    emailext (
                        body: '''${SCRIPT, template="component-email-dev.template"}''',
                        mimeType: 'text/html',
                        subject: "[Jenkins] S3Main PostMerge : ${currentBuild.currentResult}, ${JOB_BASE_NAME}#${BUILD_NUMBER}",
                        attachLog: true,
                        to: toEmail,
                        recipientProviders: recipientProvidersClass
                    )
                }else {
                   echo 'Skipping Notification....' 
                }
            }
        }    
    }
}

@NonCPS
def getAuthor(issue) {

    def changeLogSets = currentBuild.rawBuild.changeSets
    def author= ""
    def response = ""
    // Grab build information
    for (int i = 0; i < changeLogSets.size(); i++){
        def entries = changeLogSets[i].items
        for (int j = 0; j < entries.length; j++) {
            def entry = entries[j]
            if((entry.msg).contains(issue)){
                author = entry.author
            }
        }
    }
    response = "* Author: "+author+"\n"
    return response
}
