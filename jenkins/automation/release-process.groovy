#!/usr/bin/env groovy
pipeline {

    agent {
        node {
            label 'docker-centos-7.9.2009-node'
        }
    }

    parameters {
        string(name: 'BASE_TAG', defaultValue: '', description: 'Image tag which need to be tagged with TARGET_TAG. It is used to tag component commits and create GitHub Release as well')
        string(name: 'TARGET_TAG', defaultValue: '', description: 'New tag to be tagged on Image')
        string(name: 'SERVICES_RELEASE', defaultValue: '', description: 'Services(cortx-k8s) release version on which image deployment is tested')
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for tagging/Changelog/GitHub Release scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for tagging/Changelog/GitHub Release scripts', trim: true)
    }

    environment {
        GITHUB_TOKEN = credentials('cortx-admin-token')
        EMAIL_RECIPIENTS = "DEBUG"
        REGISTRY = "ghcr.io"
        OWNER = "seagate"
        LATEST_GH_SERVER_IMAGE_TAG = sh( script: """
            curl -s -H \"Accept: application/vnd.github+json\" -H \"Authorization: token ${GITHUB_TOKEN}\" \"https://api.github.com/repos/${OWNER}/cortx-re/releases/latest\" | jq '.tag_name' | tr -d '\"'
        """, returnStdout: true).trim()
        CORTX_SERVER_IMAGE = "${REGISTRY}/${OWNER}/cortx-rgw:${BASE_TAG}"
        LATEST_GH_SERVER_IMAGE = "${REGISTRY}/${OWNER}/cortx-rgw:${LATEST_GH_SERVER_IMAGE_TAG}"
        
    }

    stages {
        stage("Tag Images") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def image_tag_info = build job: 'Cortx-Automation/Release-Process/Image-Tagging/', wait: true,
                    parameters: [
                        string(name: 'BASE_TAG', value: "${BASE_TAG}"),
                        string(name: 'TARGET_TAG', value: "${TARGET_TAG}"),
                        string(name: 'EMAIL_RECIPIENTS', value: "${EMAIL_RECIPIENTS}"),
                        string(name: 'DOCKER_REGISTRY', value: "${REGISTRY}")
                    ]    
                }
            }        
        }

        stage("Tag Component Commits") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def commit_tag_info = build job: 'Release_Engineering/re-workspace/Tagging', wait: true,
                    parameters: [
                        string(name: 'CORTX_IMAGE', value: "${CORTX_SERVER_IMAGE}"),
                        string(name: 'GIT_TAG', value: "${BASE_TAG}"),
                        string(name: 'TAG_MESSAGE', value: "CORTX Release ${BASE_TAG}"),
                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                        string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}")
                    ]    
                }
            }        
        }

        stage("Generate Changelog") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def changelog_info = build job: 'Release_Engineering/Cortx-Automation/changelog-generation', wait: true,
                    parameters: [
                        string(name: 'BUILD_FROM', value: "${LATEST_GH_SERVER_IMAGE}"),
                        string(name: 'BUILD_TO', value: "${CORTX_SERVER_IMAGE}")
                    ]
                    env.changeseturl = sh( script: "echo ${changelog_info.absoluteUrl}artifact/CHANGESET.md", returnStdout: true)  
                }
            }        
        }

        stage("Create GitHub Release") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    def gh_release = build job: 'Cortx-Automation/Release-Process/Create-GitHub-Release', wait: true,
                    parameters: [
                        string(name: 'GIT_TAG', value: "${BASE_TAG}"),
                        string(name: 'SERVICES_VERSION', value: "${SERVICES_RELEASE}"),
                        string(name: 'CHANGESET_URL', value: "${env.changeseturl}"),
                        string(name: 'CORTX_RE_BRANCH', value: "${CORTX_RE_BRANCH}"),
                        string(name: 'CORTX_RE_REPO', value: "${CORTX_RE_REPO}")
                    ]
                }
            }        
        }
    }    
}