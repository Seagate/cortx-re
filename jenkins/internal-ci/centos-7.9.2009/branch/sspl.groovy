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
        component = "sspl"
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
                    dir ('cortx-sspl') {
                        checkout([$class: 'GitSCM', branches: [[name: "*/${branch}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-monitor']]])
                    }
                }
            }
            
            stage('Build') {
                steps {
                    script { build_stage = env.STAGE_NAME }
                    sh label: 'Build', returnStatus: true, script: '''
                        set -xe
                        pushd cortx-sspl
                        VERSION=$(cat VERSION)
                        export build_number=${BUILD_ID}
                        #Execute build script
                        echo "Executing build script"
                        echo "VERSION:$VERSION"
                        ./jenkins/build.sh -v $version -l DEBUG
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
                steps {
                    script { build_stage = env.STAGE_NAME }
                    sh label: 'Tag last_successful', script: '''
                    pushd $build_upload_dir/
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
                        env.release_build_location="http://cortx-storage.colo.seagate.com/releases/cortx/github/$branch/$os_version/${env.release_build}"
                    }
                }
            }
            
            stage ('Test') {
                when { triggeredBy 'SCMTrigger' }
                steps {
                    script { build_stage = env.STAGE_NAME }
                    script {
                        build job: "../../SSPL/SSPL_Build_Sanity_${os_version}", propagate: false, wait: false,  parameters: [string(name: 'TARGET_BUILD', value: "main:${env.release_build}")]
                    }
                }
            }
            
            stage('Update Jira') {
                when { expression { return env.release_build != null } }
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
                        }
                    }
            }
        }
    }

    post {
        always {
            script  {        

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
