pipeline {
	 	 
    agent {
		node {
            // Agent created with 4GB ram/16GB memory in EOS_SVC_RE1 account 
			label "s3-codecoverage-test"
		}
	}

	options {
		timestamps() 
	}

    triggers {
        // Scheduled to run on daily ~ 1-2 AM IST
		cron('H 20 * * *')
	}

	environment {  
		S3_URL = "https://github.com/Seagate/cortx-s3server"
        S3_BRANCH = "main"
        CODACY_URL = "https://app.codacy.com/gh/Seagate/cortx-s3server/dashboard"
        COMPONENT = "CORTX-S3Server"
	}
	
	stages {	

        // Clones s3server source code into workspace
        stage ('Checkout') {
            when { expression { true } }
            steps {
                script {         
                    build_stage = env.STAGE_NAME

                    step([$class: 'WsCleanup'])

                    dir('cortx-s3server') {
                        checkout([$class: 'GitSCM', branches: [[name: "${S3_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[url: '${S3_URL}']]])
                        env.S3_COMMIT_ID = sh(returnStdout: true, script: 'git rev-parse HEAD').trim()
                    }    
                }
            }
        }

        // Cleanup previous build and starts s3server build on latest source code
        stage ('Build') {
            steps {
                script { build_stage = env.STAGE_NAME }
                dir('cortx-s3server'){
                    script {
					    try {
					        sh label: 'cleanup', script: """
                                rm -rf /root/.seagate_src_cache/
                                rm -rf /root/.cache/bazel/*
                                rm -rf /var/motr/*
                                rm -rf /var/log/seagate/*
                                rm -rf  /root/rpmbuild/*/*

                                sh scripts/s3-logrotate/s3supportbundlefilerollover.sh
                            """
					        sh label: 'build', script: """
					            git checkout -b ${S3_BRANCH}
					            git branch
					            hostname
						        ./scripts/env/dev/init.sh -s
						    """
					    } catch (err) {
								build_stage = env.STAGE_NAME
								error "Failed to Build S3"
					    }
					}
                }
            }
        }
		
		stage ('Code Coverage') {
			steps {
				withCredentials([string(credentialsId: 'S3-CODACY-TOKEN', variable: 'TOKEN')]) {
					script { build_stage = env.STAGE_NAME }
					dir('cortx-s3server'){
						script {
							try {
								sh label: 'code coverage', script: """
									git branch
									pip3 install coverage
									./scripts/coverage/run_all_coverage.sh -t ${TOKEN}
						
								"""
							} catch (err) {
								build_stage = env.STAGE_NAME
								error "Failed to generate or upload code coverage"
							}
						}
					}
				}
			}
		}
    }
	post {
		success {
			sh label: 'Code Coverage Upload Done', script: '''
			echo "Code Coverage Successful"
            '''
        }
		always {
			withCredentials([string(credentialsId: 'codacy-token', variable: 'CODACYTOKEN')]) {
				script {
					try {
						sh label: 'download_log_files', returnStdout: true, script: """ 
							# pip3 install json2html							
							python3 CodacyCodeCoverage.py cortx-s3server ${CODACYTOKEN} > s3server.html
						"""
					} catch (err) {
						echo err.getMessage()
					}
				
				
					env.component_name = "${COMPONENT}"
					env.build_stage = "${build_stage}"
					env.code_coverage_url = "${CODACY_URL}"
					def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
					
				    def mailRecipients = "priyank.p.dalal@seagate.com,shailesh.vaidya@seagate.com,mukul.malhotra@seagate.com,nilesh.govande@seagate.com,puja.mudaliar@seagate.com"
                
					if (fileExists('s3server.html')){
						try {
							file_status = readFile(file: 's3server.html')
							env.failure_cause = file_status
							//MESSAGE = deployment_status.split('\n')[1].split('.')[0].trim()
						} catch (err) {
							echo err.getMessage()
						}
					}
                
					emailext (
                    
						body: '''${SCRIPT, template="code-coverage-email.template"} , ${FILE,path="s3server.html"} ''',
						mimeType: 'text/html',
						subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME} Status",
						attachLog: true,
						to: "${mailRecipients}",
						recipientProviders: recipientProvidersClass
					)
				}
			}
		}
	}
}
