#!/usr/bin/env groovy
pipeline {

    agent {
        node {
            label 'docker-centos-7.9.2009-node'
        }
    }

    parameters {
        string(name: 'GIT_TAG', defaultValue: '', description: 'Image tag/Commit tag required for GitHub Release')
        string(name: 'RELEASE_REPO', defaultValue: 'Seagate/cortx', description: 'owner/repository-name where release need to be created')
        string(name: 'SERVICES_VERSION', defaultValue: '', description: 'Services(cortx-k8s) version on which image deployment is tested')
        string(name: 'CHANGESET_URL', defaultValue: '', description: 'CHNAGESET.md file url.')
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for GitHub Release script', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for GitHub release script', trim: true)
    }

    environment {
        IMAGE_TAGS = getImageTags("cortx-rgw", "2.0.0-895")
    }

    stages {
        stage('Checkout Script') {
            steps {
                script { build_stage = env.STAGE_NAME }             
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]])
                }
            }
        }
        
        stage("Create GitHub Release") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    withCredentials([string(credentialsId: 'gaurav-github-token', variable: 'GH_TOKEN')]) {
                        env.github_release_url = sh( script: """
                            bash scripts/release_support/create-cortx-github-release.sh -t $GIT_TAG -v $SERVICES_VERSION -c $CHANGESET_URL -r $RELEASE_REPO
                        """, returnStdout: true).trim()
                    }			
                }
            }
	    }
    }
    post {
        always {

            script {

                // Jenkins Summary
                if ( currentBuild.currentResult == "SUCCESS" ) { 
                    MESSAGE = "CORTX Release: ${IMAGE_TAGS} (Available on GitHub)"
                    ICON = "accept.gif"
                    STATUS = "SUCCESS"
                } else if ( currentBuild.currentResult == "FAILURE" ) {
                    manager.buildFailure()
                    MESSAGE = "Failure: GitHub Release ${GIT_TAG}"
                    ICON = "error.gif"
                    STATUS = "FAILURE"
 
                } else {
                    manager.buildUnstable()
                    MESSAGE = "GitHub release creation is Unstable"
                    ICON = "warning.gif"
                    STATUS = "UNSTABLE"
                }

                manager.createSummary("${ICON}").appendText("<h3>CORTX GitHub Release creation ${currentBuild.currentResult} </h3><p>Please check <a href=\"${BUILD_URL}/console\">GitHub release logs</a> for more info", false, false, false, "red")

                // Email Notification
                env.build_stage = "${build_stage}"
                env.tags = "${IMAGE_TAGS}"

                def toEmail = "gaurav.chaudhari@seagate.com"
                def recipientProvidersClass = [[$class: 'DevelopersRecipientProvider']]
                if ( manager.build.result.toString() == "FAILURE" ) {
                    toEmail = "${toEmail}"
                    recipientProvidersClass = [[$class: 'DevelopersRecipientProvider'], [$class: 'RequesterRecipientProvider']]
                }
               
                emailext ( 
                    body: '''${SCRIPT, template="github-release-email.template"}''',
                    mimeType: 'text/html',
                    subject: "${MESSAGE}",
                    attachLog: true,
                    to: toEmail,
                    recipientProviders: recipientProvidersClass
                )

                archiveArtifacts artifacts: "api_response.html", onlyIfSuccessful: false, allowEmptyArchive: true
            }
            cleanWs()
        }
    }            
}

def getImageTags(image, tag) {
    withCredentials([string(credentialsId: 'gaurav-github-token', variable: 'GH_TOKEN')]) {
        image_tags = sh( script: """
            tags=\$( curl -s -H \"Accept: application/vnd.github+json\" -H \"Authorization: token ${GH_TOKEN}\" \"https://api.github.com/orgs/seagate/packages/container/${image}/versions\" | jq '.[] | select(.metadata.container.tags[]==\"${tag}\") | .metadata.container.tags[]' | awk '!/2.0.0-latest/' | tr -d '\"' )
            echo \${tags} | tr ' ' '/'
        """, returnStdout: true).trim()
        return image_tags
    }
}