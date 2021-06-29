#!/usr/bin/env groovy
pipeline {
    agent {
        node {
            label 'ova-builder'
        }
    }
    parameters {
        string(name: 'BUILD_URL', defaultValue: 'http://cortx-storage.colo.seagate.com/releases/opensource_builds/ova_builds/centos-7.8.2003/ova-build-42/', description: 'CORTX release build url.')
        string(name: 'HOST', defaultValue: '10.230.245.7', description: 'IP address of VM deploy host')
        string(name: 'VSPHERE_VM_NAME', defaultValue: 'cortx-sms-ova', description: 'Name VM deploy host')
        string(name: 'VSPHERE_VM_SNAPSHOT_NAME', defaultValue: 'cortx-sms-ova-snap', description: 'Snapshot name of VM deploy host')
	    choice(name: 'CREATE_VM', choices: [ "false", "true" ], description: 'Create a VM from generated OVA')
	    string(name: 'OVA_VM_NAME', defaultValue: 'test-ova-vm', description: 'Name for OVA VM. Applicable only if CREATE_VM=true')
	    choice(name: 'OVA_TESTER', choices: [ "false", "true" ], description: 'Run pre-setup and basic tests on OVA VM. Applicable only if CREATE_VM=true')
    }
    environment {
        DEPLOYMENT_HOST_SSH_KEY = credentials('dev-server-sshkey')
        S3_SECRET_KEY = credentials('s3-dummy-secret-key')
        NODE_SSH_PASS = credentials('cortx_ova_creds')
        VSPHERE_HOSTNAME = "10.230.240.78"
    }
    options {
        timeout(time: 300, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
        buildDiscarder(logRotator(numToKeepStr: "30"))
    }
    stages {
        stage('Checkout') {
            steps {
                script{
                    dir('cortx-re') {
                        checkout([$class: 'GitSCM',
                            branches: [[name: '*/cortx-1.0']],
                            doGenerateSubmoduleConfigurations: false,
                            extensions: [],
                            submoduleCfg: [],
                            userRemoteConfigs: [[credentialsId: 'github-access', url: 'https://github.com/Seagate/cortx-re.git']]

                        ])
                    }
                }
            }
        }
        stage('Revert VM to base image') {
            steps {
                script {
                    executeAnsiblePlaybook("snapshot-revert")
                }
            }
        }
        stage('SSH Setup') {
            steps {
                script {
                    executeAnsiblePlaybook("ssh-setup")
                }
            }
        }
        stage('CORTX setup on VM') {
            steps {
                script {
                    executeAnsiblePlaybook("install-cortx")
                }
            }
        }
        stage('Build OVA') {
            steps {
                sleep time: 60, unit: 'SECONDS' 
                script {
		    executeAnsiblePlaybook("ovabuild")
		}
            }
        }
	stage('Create OVA VM') {
            when { expression { params.CREATE_VM == 'true' } }
            steps {
                sleep time: 30, unit: 'SECONDS'
                script {
                    executeAnsiblePlaybook("create-vm")
                }
            }
        }
        stage('OVA VM Tester') {
            when { expression { params.OVA_TESTER == 'true' } }
            steps {
                sleep time: 60, unit: 'SECONDS'
                script {
                    executeOVATesterPlaybook()
                }
            }
        }		
    }
}

def executeAnsiblePlaybook(tags) {
    withCredentials([usernamePassword(credentialsId: 'vsphere_creds', passwordVariable: 'VSPHERE_PASSWORD', usernameVariable: 'VSPHERE_USERNAME'), usernamePassword(credentialsId: 'cortx_test_setup_creds', passwordVariable: 'TEST_PASS', usernameVariable: 'TEST_USER')]) {

        dir('cortx-re/scripts/ova-release') {
            ansiblePlaybook(
                playbook: 'cortx_ova_release.yml',
                inventory: 'inventories/development/hosts',
                tags: "${tags}",
                extraVars: [
                        "BUILD_URL"                 : [value: "${BUILD_URL}", hidden: false],
                        "ansible_host"              : [value: "${HOST}", hidden: false],
                        "DEPLOYMENT_HOST_SSH_KEY"   : [value: "${DEPLOYMENT_HOST_SSH_KEY}", hidden: true],
                        "NODE_SSH_PASS"             : [value: "${NODE_SSH_PASS}", hidden: true],
                        "VSPHERE_VM_NAME"           : [value: "${VSPHERE_VM_NAME}", hidden: true],
                        "VSPHERE_VM_SNAPSHOT_NAME"  : [value: "${VSPHERE_VM_SNAPSHOT_NAME}", hidden: true],
                        "VSPHERE_HOSTNAME"          : [value: "${VSPHERE_HOSTNAME}", hidden: true],
                        "VSPHERE_PASSWORD"          : [value: "${VSPHERE_PASSWORD}", hidden: true],
                        "VSPHERE_USERNAME"          : [value: "${VSPHERE_USERNAME}", hidden: true],
                        "TEST_PASS"                 : [value: "${TEST_PASS}", hidden: true],
                        "TEST_USER"                 : [value: "${TEST_USER}", hidden: true],
                        "S3_SECRET_KEY"             : [value: "${S3_SECRET_KEY}", hidden: true], 
                        "OVA_VM_NAME"               : [value: "${OVA_VM_NAME}", hidden: false]
                ],
                colorized: true
            )
        }
    }
}

def executeOVATesterPlaybook() {
    withCredentials([usernamePassword(credentialsId: 'vsphere_creds', passwordVariable: 'VSPHERE_PASSWORD', usernameVariable: 'VSPHERE_USERNAME'), usernamePassword(credentialsId: 'cortx_test_setup_creds', passwordVariable: 'TEST_PASS', usernameVariable: 'TEST_USER')]) {

        dir('cortx-re/ova/') {
            ansiblePlaybook(
                playbook: 'cortx_ova_release.yml',
                inventory: 'inventories/development/hosts',
                tags: "ova-tester",
                extraVars: [
                        "DEPLOYMENT_HOST_SSH_KEY"   : [value: "${DEPLOYMENT_HOST_SSH_KEY}", hidden: true],
                        "NODE_SSH_PASS"             : [value: "${NODE_SSH_PASS}", hidden: true],
                        "VSPHERE_HOSTNAME"          : [value: "${VSPHERE_HOSTNAME}", hidden: true],
                        "VSPHERE_PASSWORD"          : [value: "${VSPHERE_PASSWORD}", hidden: true],
                        "VSPHERE_USERNAME"          : [value: "${VSPHERE_USERNAME}", hidden: true],
                        "TEST_PASS"                 : [value: "${TEST_PASS}", hidden: true],
                        "TEST_USER"                 : [value: "${TEST_USER}", hidden: true],
                        "OVA_VM_NAME"               : [value: "${OVA_VM_NAME}", hidden: false]
                ],
                colorized: true
            )
        }
    }
}
