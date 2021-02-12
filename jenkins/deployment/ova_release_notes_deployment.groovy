#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            label 'prvsnr-code-coverage'
        }  
    }
    parameters {  
        string(name: 'QUERY', defaultValue: 'Sprint = EES_Sprint-33 AND resolutiondate >= "2020-12-17 00:00" AND resolutiondate <= "2020-12-29 00:00"', description: 'JIRA Query')
        string(name: 'OVA_BUILD', defaultValue: "21", description: 'OVA build number')
        string(name: 'GITHUB_RELEASE', defaultValue: "", description: 'GitHub release number')
        string(name: 'SOURCE_BUILD', defaultValue: "531", description: 'Source cortx build number')
        string(name: 'TARGET_BUILD', defaultValue: "571", description: 'Target cortx build number')
    }
    environment {
	GITHUB_TOKEN = credentials('gaurav-github-token')
    }    
    options {
        timeout(time: 15, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        buildDiscarder(logRotator(numToKeepStr: "30"))
    }
    stages {
        stage('Checkout') {
            steps {
                script{
                        
                            checkout([$class: 'GitSCM', 
                                branches: [[name: '*/main']], 
                                doGenerateSubmoduleConfigurations: false, 
                                extensions: [], 
                                submoduleCfg: [], 
                                userRemoteConfigs: [[credentialsId: 'b4d16443-7ed3-4ca9-91e6-10ea5bdba7fc', url: 'https://github.com/Seagate/cortx-re.git']]
                    
                            ])
                        
                }    
            }
        }
        stage('Execute Script') {
            steps {
                dir("scripts/release_support/ova-release-notes") {
                    withCredentials([usernamePassword(credentialsId: 'GauravVMNewPass', passwordVariable: 'VM_PASS', usernameVariable: 'VM_USER')]) {
                        sh"python3 ova_release.py -u ${VM_USER} -p ${VM_PASS} --query '${QUERY}' --build ${OVA_BUILD} --release ${GITHUB_RELEASE} --sourceBuild ${SOURCE_BUILD} --targetBuild ${TARGET_BUILD}"
                    }
                }    
            }
        }
    }
}
