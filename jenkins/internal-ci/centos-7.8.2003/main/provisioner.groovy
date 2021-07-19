#!/usr/bin/env groovy
pipeline { 
    agent {
        node {
            label 'docker-cp-centos-7.8.2003-node'
        }
    }
    
	triggers {
        pollSCM '*/5 * * * *'
    }
	
	environment {
        version = "2.0.0"
        env = "dev"
		component = "provisioner"
        branch = "main"
        os_version = "centos-7.8.2003"
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
					checkout([$class: 'GitSCM', branches: [[name: "*/${branch}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-prvsnr.git']]])
            }
        }

        stage('Install Dependencies') {
            steps {
				script { build_stage = env.STAGE_NAME }
                sh encoding: 'utf-8', label: 'Install Python', returnStdout: true, script: 'yum install -y python'
                sh encoding: 'utf-8', label: 'Cleanup', returnStdout: true, script: 'test -d /root/rpmbuild && rm -rf /root/rpmbuild || echo "/root/rpmbuild absent. Skipping cleanup..."'
            }
        }

        stage('Build') {
            steps {
				script { build_stage = env.STAGE_NAME }
                sh encoding: 'utf-8', label: 'Provisioner RPMS', returnStdout: true, script: """
                    sh ./devops/rpms/buildrpm.sh -g \$(git rev-parse --short HEAD) -e $version -b ${BUILD_NUMBER}
                """
                sh encoding: 'utf-8', label: 'Provisioner CLI RPMS', returnStdout: true, script: """
				    sh ./cli/buildrpm.sh -g \$(git rev-parse --short HEAD) -e $version -b ${BUILD_NUMBER}
                """
				sh encoding: 'UTF-8', label: 'cortx-setup', script: """
                if [ -f "./devops/rpms/node_cli/node_cli_buildrpm.sh" ]; then
                    sh ./devops/rpms/node_cli/node_cli_buildrpm.sh -g \$(git rev-parse --short HEAD) -e 2.0.0 -b ${BUILD_NUMBER}
                else
                    echo "node_cli package creation is not implemented"
                fi
                """
				
				sh encoding: 'UTF-8', label: 'api', script: '''
					bash ./devops/rpms/api/build_python_api.sh -vv --out-dir /root/rpmbuild/RPMS/x86_64/ --pkg-ver ${BUILD_NUMBER}_git$(git rev-parse --short HEAD)
				'''

				sh encoding: 'UTF-8', label: 'cortx-setup', script: '''
					bash ./devops/rpms/lr-cli/build_python_cortx_setup.sh -vv --out-dir /root/rpmbuild/RPMS/x86_64/ --pkg-ver ${BUILD_NUMBER}_git$(git rev-parse --short HEAD)
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
			steps {
				script { build_stage = env.STAGE_NAME }
				sh label: 'Tag last_successful', script: '''pushd $build_upload_dir/
                    test -d $build_upload_dir/last_successful && rm -f last_successful
                    ln -s $build_upload_dir/$BUILD_NUMBER last_successful
                    popd
				'''
			}
		}

        stage ("Release") {
            when { triggeredBy 'SCMTrigger' }
            steps {
                script { build_stage = env.STAGE_NAME }
				script {
                	def releaseBuild = build job: 'Main Release', propagate: true
				 	env.release_build = releaseBuild.number
                    env.release_build_location="http://cortx-storage.colo.seagate.com/releases/cortx/github/$branch/$os_version/${env.release_build}"
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
				env.release_build = (env.release_build != null) ? env.release_build : "" 
				env.release_build_location = (env.release_build_location != null) ? env.release_build_location : ""
				env.component = (env.component).capitalize()
				env.build_stage = "${build_stage}"

                env.vm_deployment = (env.deployVMURL != null) ? env.deployVMURL : "" 
                if ( env.deployVMStatus != null && env.deployVMStatus != "SUCCESS" && manager.build.result.toString() == "SUCCESS" ) {
                    manager.buildUnstable()
                }

				def toEmail = ""
				def recipientProvidersClass = [[$class: 'DevelopersRecipientProvider']]
				if( manager.build.result.toString() == "FAILURE" ) {
					toEmail = "CORTX.Provisioner.Re@seagate.com,shailesh.vaidya@seagate.com"
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
