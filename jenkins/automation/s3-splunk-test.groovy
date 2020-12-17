pipeline {
	 	 
    agent {
		node {
			label 's3-splunk-test'
			}
	}

	options {
		timestamps() 
	}

    parameters {  
	    string(name: 'S3_URL', defaultValue: 'https://github.com/Seagate/cortx-s3server', description: 'Repo for S3Server')
        string(name: 'S3_BRANCH', defaultValue: 'main', description: 'Branch for S3Server')
        
	}

    environment {

        S3_LDAP_CRED = credentials("s3-test-ldap-cred")

        S3_MAIN_USER = "s3-test-main"
        S3_EXT_USER = "s3-test-ext"
		
	}
 	
	stages {	

        stage ('Build S3Server') {

            when { expression { true  } }
            
            steps {
                script {
                    
                    build_stage=env.STAGE_NAME

                    step([$class: 'WsCleanup'])

                    dir('s3server'){

                        checkout([$class: 'GitSCM', branches: [[name: "${S3_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: '${S3_URL}']]])
  
                        // sh label: 'build', script: """
                        //     rm -rf /root/.seagate_src_cache/
                        //     rm -rf /root/.cache/bazel/*
                        //     rm -rf /var/motr/*
                        //     rm -rf /var/log/seagate/*
                        //     rm -rf  /root/rpmbuild/*/*
                        //     rm -rf /root/splunktestfilev2.txt
                        //     rm -rf /root/splunktestfilev4.txt

                        //     yum install python36 -y 

                        //     ./jenkins-build.sh --automate_ansible 
                        // """
                    }
                    
                }
            }
        }	

        stage ('Execute S3 Splunk Test') {
            steps {
                
                script { build_stage=env.STAGE_NAME }

                dir('cortx-re'){
                    checkout([$class: 'GitSCM', branches: [[name: "EOS-15690"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/gowthamchinna/cortx-re']]])
                }
                
                sh label: 'create s3 account', script: '''
                
                    cp ./cortx-s3server/scripts/splunkscript/splunktestfilev2.txt /root/splunktestfilev2.txt
                    cp ./cortx-s3server/scripts/splunkscript/splunktestfilev4.txt /root/splunktestfilev4.txt 
                    
                    # Create Main & Ext user accounts. These accounts are used for splunk test 
                    ./cortx-re/scripts/automation/s3-test/create_s3_account.sh ${S3_MAIN_USER}_${BUILD_NUMBER} ${S3_MAIN_USER}_${BUILD_NUMBER}@seagate.com $S3_LDAP_CRED_USR $S3_LDAP_CRED_PSW 2>&1 | tee s3_account_main.txt
                    ./cortx-re/scripts/automation/s3-test/create_s3_account.sh ${S3_EXT_USER}_${BUILD_NUMBER} ${S3_EXT_USER}_${BUILD_NUMBER}@seagate.com $S3_LDAP_CRED_USR $S3_LDAP_CRED_PSW 2>&1 | tee s3_account_ext.txt

                    # Set Main & Ext user cred in environment variable. This is required in splunk config file
                    S3_MAIN_USER_ID=$(cat s3_account_main.txt | grep "AccountId" | cut -d, -f2 | cut -d= -f2 | xargs)
                    S3_MAIN_ACCESS_KEY=$(cat s3_account_main.txt | grep "AccountId" | cut -d, -f4 | cut -d= -f2 | xargs)
                    S3_MAIN_SECRETE_KEY=$(cat s3_account_main.txt | grep "AccountId" | cut -d, -f5 | cut -d= -f2 | xargs)
                    S3_EXT_USER_ID=$(cat s3_account_ext.txt | grep "AccountId" | cut -d, -f2 | cut -d= -f2 | xargs)
                    S3_EXT_ACCESS_KEY=$(cat s3_account_ext.txt | grep "AccountId" | cut -d, -f4 | cut -d= -f2 | xargs)
                    S3_EXT_SECRETE_KEY=$(cat s3_account_ext.txt | grep "AccountId" | cut -d, -f5 | cut -d= -f2 | xargs)

                    # Update Splunk file
                    sed -i "/^host /s/=.*$/= s3.seagate.com/; /^is_secure /s/=.*$/= no/; /^bucket prefix /s/=.*$/= s3-splunk-test-{random}-/; 0,/^user_id /s/=.*$/= ${S3_MAIN_USER_ID}/; 0,/^access_key /s/=.*$/= ${S3_MAIN_ACCESS_KEY}/; 0,/^secret_key /s/=.*$/= ${S3_MAIN_SECRETE_KEY}/; 1,/^user_id /s/=.*$/= ${S3_EXT_USER_ID}/; 1,/^access_key /s/=.*$/= ${S3_EXT_ACCESS_KEY}/; 1,/^secret_key /s/=.*$/= ${S3_EXT_SECRETE_KEY}/;" /root/splunk.conf

                    # Execute the test script
                    sh cortx-s3server/scripts/splunkscript/splunk_script.sh

                '''
            }
        }	
    }
        
    post {
        always {

            script{
                echo 'Cleanup Workspace.'
                deleteDir() /* clean up our workspace */
                sh label: 'cleanup', script: '''
                    rm -rf /root/.seagate_src_cache/
                '''
                env.release_build = "${BUILD_NUMBER}"
                env.branch_name = "${S3_BRANCH}"
                env.build_stage = "${build_stage}"
                
                def mailRecipients = "gowthaman.chinnathambi@seagate.com"
                def jobName = currentBuild.fullDisplayName
                
                emailext body: '''${SCRIPT, template="pre-merge-email.template"}''',
                mimeType: 'text/html',
                recipientProviders: [requestor()], 
                subject: "[Jenkins] ${jobName}",
                attachLog: true,
                to: "${mailRecipients}",
                replyTo: "${mailRecipients}"
            }
        }

        success {
            sh label: 'cleanup', script: '''
                rm -rf /var/mero/*
                rm -rf /var/motr/*
                rm -rf /var/log/seagate/*
                rm -rf  /root/rpmbuild/*/*
                rm -rf /root/.cache/bazel/*
            '''
        }
    }	
}