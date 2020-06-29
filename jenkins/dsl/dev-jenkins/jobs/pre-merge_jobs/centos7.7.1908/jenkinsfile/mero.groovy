#!/usr/bin/env groovy
pipeline {
	agent {
		node {
			label 'docker-centos7.7.1908-mero-premerge-node'
		}
	}

	parameters {
        string(name: 'branch', defaultValue: 'master', description: 'Branch Name')
    }
		
	environment {    
        env="dev"
		component="mero"
        os_version="centos-7.7.1908"
		pipeline_group="pre-merge"
        release_dir="/mnt/bigstorage/releases/eos"
        build_upload_dir="${release_dir}/components/${pipeline_group}/${os_version}/${env}/${component}"

        // Dependent component job build
        build_upload_dir_s3="$release_dir/components/${pipeline}/${os_version}/${env}/s3server"
        build_upload_dir_hare="$release_dir/components/${pipeline}/${os_version}/${env}/hare"

		// Param hack for initial config
        branch="${branch != null ? branch : 'master'}"
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
				script { build_stage=env.STAGE_NAME }
				dir ('mero') {
                	checkout([$class: 'GitSCM', branches: [[name: "*/${branch}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CheckoutOption'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: false, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[url: 'http://gerrit.mero.colo.seagate.com:8080/mero']]])
			    }
			}
		}
	
    	stage('Install Dependencies') {
		    steps {
				script { build_stage=env.STAGE_NAME }
				dir ('mero') {	
					sh label: '', script: '''
						export build_number=${BUILD_ID}
						kernel_src=$(ls -1rd /lib/modules/*/build | head -n1)
						cp mero.spec.in mero.spec
						sed -i 's/@.*@/111/g' mero.spec
						yum-builddep -y mero.spec
					'''	
				}
			}
		}

		stage('Build') {
			steps {
				script { build_stage=env.STAGE_NAME }
				dir ('mero') {	
					sh label: '', script: '''
						rm -rf /root/rpmbuild/RPMS/x86_64/*.rpm
						KERNEL=/lib/modules/$(yum list installed kernel | tail -n1 | awk '{ print $2 }').x86_64/build
						./autogen.sh
						./configure --with-linux=$KERNEL
						export build_number=${BUILD_ID}
						make rpms
					'''
				}	
			}
		}
		
		stage ('Upload') {
			steps {
				script { build_stage=env.STAGE_NAME }
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
				script { build_stage=env.STAGE_NAME }
				sh label: 'Tag last_successful', script: '''
					pushd $build_upload_dir/
					test -d $build_upload_dir/current_build && rm -f current_build
					ln -s $build_upload_dir/$BUILD_NUMBER current_build
					popd
				'''
			}
		}
		
		stage ("Trigger Downstream Jobs") {
			parallel {
				stage ("build S3Server") {
					steps {
						script { build_stage=env.STAGE_NAME }
                        script{
							def s3Build = build job: 'S3Server', wait: true, parameters: [string(name: 'branch', value: "master")]
							env.S3_BUILD_NUMBER = s3Build.number
						}
					}
				}
					        
		        stage ("build Hare") {
					steps {
						script { build_stage=env.STAGE_NAME }
                        script{
							def hareBuild = build job: 'Hare', wait: true, parameters: [string(name: 'branch', value: "master")]
							env.HARE_BUILD_NUMBER = hareBuild.number
						}
					}
				}
			}	
		}
	
		stage ('Tag last_successful') {
			steps {
				script { build_stage=env.STAGE_NAME }
				sh label: 'Tag last_successful', script: '''pushd $build_upload_dir/
					test -d $build_upload_dir/last_successful && rm -f last_successful
					ln -s $build_upload_dir/$BUILD_NUMBER last_successful
					popd
				'''
                sh label: 'Tag last_successful for dep component', script: '''
					# Hare Build 
                    test -d $build_upload_dir_hare/last_successful && rm -f $build_upload_dir_hare/last_successful
					ln -s $build_upload_dir_hare/$HARE_BUILD_NUMBER $build_upload_dir_hare/last_successful

                    # S3Server Build
                    test -d $build_upload_dir_s3/last_successful && rm -f $build_upload_dir_s3/last_successful
					ln -s $build_upload_dir_s3/$S3_BUILD_NUMBER $build_upload_dir_s3/last_successful
				'''
			}
		}

        stage ('Copy to New NFS') {
			steps {
                catchError(buildResult: 'SUCCESS') {
                    script { build_stage=env.STAGE_NAME }
                    sh label: 'copy_to_new_nfs', script: '''

                        nfs_upload_path=/mnt/data1/releases/eos/components/${pipeline_group}/${os_version}/${env}/${component}
                        mkdir -p $nfs_upload_path

                        pushd $nfs_upload_path
                            
                            test -d $nfs_upload_path/$BUILD_NUMBER && rm -f $nfs_upload_path/$BUILD_NUMBER
                            cp -R $build_upload_dir/$BUILD_NUMBER $nfs_upload_path/$BUILD_NUMBER
                            
                            test -d $nfs_upload_path/last_successful && rm -f last_successful
                            ln -s $nfs_upload_path/$BUILD_NUMBER last_successful
                        popd
                    '''
                }
			}
		}	

		stage ("Release") {
            steps {
                script { build_stage=env.STAGE_NAME }
				script {
                	def releaseBuild = build job: 'Pre-merge Release', propagate: false, parameters: [string(name: 'release_component', value: "${component}")]
				 	env.release_build = releaseBuild.number
                    env.release_build_location="http://ci-storage.mero.colo.seagate.com/releases/eos/$pipeline_group/$os_version/"+releaseBuild.number+"_${component}"
				}
            }
        } 
	}

	post {
		always{                
			script{
				env.release_build = (env.release_build != null) ? env.release_build : "" 
				env.release_build_location = (env.release_build_location != null) ? env.release_build_location : ""
				env.component = (env.component).capitalize()
				env.build_stage = "${build_stage}"

				def toEmail = "gowthaman.chinnathambi@seagate.com"
				emailext (
					body: '''${SCRIPT, template="component-email.template"}''',
					mimeType: 'text/html',
					subject: "${env.JOB_BASE_NAME} Pre-Merge Build ${currentBuild.currentResult}",
					attachLog: true,
					to: toEmail,
				)
			}
		}
	}	
}
