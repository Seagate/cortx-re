pipeline {
	 	 
    agent {
		node {
			label 'docker-rpm-sign-node-rhel-cortex'
		}
	}
	
	environment {
		branch = "main"
		os_version = "rhel-7.7.1908"
		release_dir = "/mnt/bigstorage/releases/cortx"
        integration_dir = "$release_dir/github/integration-custom-ci/release/$os_version"
        components_dir = "$release_dir/components/github/custom-ci/release/$os_version"
        release_tag = "custom-build-$BUILD_ID"
        passphrase = credentials('rpm-sign-passphrase')
		thrid_party_dir = "/mnt/bigstorage/releases/cortx/third-party-deps/rhel/rhel-7.7.1908/"
		python_deps = "/mnt/bigstorage/releases/cortx/third-party-deps/python-packages"
    }
	
	options {
		timeout(time: 120, unit: 'MINUTES')
		timestamps()
		disableConcurrentBuilds()
        ansiColor('xterm') 
	}
	
	parameters {
		string(name: 'CSM_BRANCH', defaultValue: 'release', description: 'This will be ignored by default.Set this to Cortx-v1.0.0_Beta for custom Beta build.For other builds CSM Agent and CSM Web will be used')
		string(name: 'CSM_URL', defaultValue: 'https://github.com/Seagate/cortx-csm.git', description: 'CSM URL')
		string(name: 'CSM_AGENT_BRANCH', defaultValue: 'main', description: 'Branch for CSM Agent')
		string(name: 'CSM_AGENT_URL', defaultValue: 'https://github.com/Seagate/cortx-manager', description: 'CSM_AGENT URL')
		string(name: 'CSM_WEB_BRANCH', defaultValue: 'main', description: 'Branch for CSM Web')
		string(name: 'CSM_WEB_URL', defaultValue: 'https://github.com/Seagate/cortx-management-portal', description: 'CSM WEB URL')
		string(name: 'HARE_BRANCH', defaultValue: 'main', description: 'Branch for Hare')
		string(name: 'HARE_URL', defaultValue: 'https://github.com/Seagate/cortx-hare', description: 'Hare URL')
		string(name: 'HA_BRANCH', defaultValue: 'main', description: 'Branch for Cortx-HA')
		string(name: 'HA_URL', defaultValue: 'https://github.com/Seagate/cortx-ha.git', description: 'Cortx-HA URL')
		string(name: 'MOTR_BRANCH', defaultValue: 'main', description: 'Branch for Motr')
		string(name: 'MOTR_URL', defaultValue: 'https://github.com/Seagate/cortx-motr.git', description: 'Motr URL')
		string(name: 'PRVSNR_BRANCH', defaultValue: 'main', description: 'Branch for Provisioner')
		string(name: 'PRVSNR_URL', defaultValue: 'https://github.com/Seagate/cortx-prvsnr.git', description: 'Provisioner URL')
		string(name: 'S3_BRANCH', defaultValue: 'main', description: 'Branch for S3Server')
		string(name: 'S3_URL', defaultValue: 'https://github.com/Seagate/cortx-s3server.git', description: 'S3Server URL')
		string(name: 'SSPL_BRANCH', defaultValue: 'main', description: 'Branch for SSPL')
		string(name: 'SSPL_URL', defaultValue: 'https://github.com/Seagate/cortx-monitor.git', description: 'SSPL URL')
		
		choice(
            name: 'OTHER_COMPONENT_BRANCH', 
            choices: ['main', 'stable', 'Cortx-v1.0.0_Beta', 'cortx-1.0'],
            description: 'Branch name to pick-up other components rpms'
        )
    }	
		
	stages {	
	
		stage ("Trigger Component Jobs") {
			parallel {
			
				stage ("Build Mero, Hare and S3Server") {
					steps {
						script { build_stage = env.STAGE_NAME }
						build job: 'motr-custom-build', wait: true,
						parameters: [
									string(name: 'MOTR_URL', value: "${MOTR_URL}"),
									string(name: 'MOTR_BRANCH', value: "${MOTR_BRANCH}"),
									string(name: 'S3_URL', value: "${S3_URL}"),
									string(name: 'S3_BRANCH', value: "${S3_BRANCH}"),
									string(name: 'HARE_URL', value: "${HARE_URL}"),
									string(name: 'HARE_BRANCH', value: "${HARE_BRANCH}")
								]
					}
				}
				
				stage ("Build Provisioner") {
					steps {
						script { build_stage = env.STAGE_NAME }
						build job: 'prvsnr-custom-build', wait: true,
						parameters: [
									string(name: 'PRVSNR_URL', value: "${PRVSNR_URL}"),
									string(name: 'PRVSNR_BRANCH', value: "${PRVSNR_BRANCH}")
								]
					}
				}
				
				stage ("Build HA") {
					steps {
						script { build_stage = env.STAGE_NAME }
						sh label: 'Copy RPMS', script:'''
						  if [ "$HA_BRANCH" == "Cortx-v1.0.0_Beta"  ]; then
							echo "cortx-ha does not have Cortx-v1.0.0_Beta branch."
							exit 1
						  fi
						'''  
						build job: 'cortx-ha', wait: true,
						parameters: [
									string(name: 'HA_URL', value: "${HA_URL}"),
									string(name: 'HA_BRANCH', value: "${HA_BRANCH}")
								]
					}
				}

				stage ("Build CSM") {
					steps {
						script { build_stage = env.STAGE_NAME }
						script {
							if ( env.CSM_BRANCH == 'Cortx-v1.0.0_Beta' ) {
                            echo "Using Cortx-v1.0.0_Beta branch"    
                            build job: 'csm-custom-build', wait: true,
                            parameters: [
                                        string(name: 'CSM_URL', value: "${CSM_URL}"),
                                        string(name: 'CSM_BRANCH', value: "${CSM_BRANCH}")
                                    ]
							}
                            else {
							
								build job: 'custom-csm-agent-build', wait: true,
									parameters: [
									string(name: 'CSM_AGENT_URL', value: "${CSM_AGENT_URL}"),
									string(name: 'CSM_AGENT_BRANCH', value: "${CSM_AGENT_BRANCH}")
									]
								build job: 'custom-csm-web-build', wait: true,
									parameters: [
									string(name: 'CSM_WEB_URL', value: "${CSM_WEB_URL}"),
									string(name: 'CSM_WEB_BRANCH', value: "${CSM_WEB_BRANCH}")
									]		
							}
						}	
					}	
				}

				stage ("Build SSPL") {
					steps {
						script { build_stage = env.STAGE_NAME }
						build job: 'sspl-custom-build', wait: true,
						parameters: [
									string(name: 'SSPL_URL', value: "${SSPL_URL}"),
									string(name: 'SSPL_BRANCH', value: "${SSPL_BRANCH}")
								]
					}
				}
			}	
		}

		stage('Install Dependecies') {
			steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Installed Dependecies', script: '''
                    yum install -y expect rpm-sign rng-tools genisoimage
                    systemctl start rngd
                '''	
			}
		}
			
		stage ('Collect Component RPMS') {
			steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script:'''
				  if [ "$OTHER_COMPONENT_BRANCH" == "stable"  ]; then
					RPM_COPY_PATH="/mnt/bigstorage/releases/cortx/components/github/stable/$os_version/dev/"
				  elif [ "$OTHER_COMPONENT_BRANCH" == "main"  ]; then
						RPM_COPY_PATH="/mnt/bigstorage/releases/cortx/components/github/main/$os_version/dev/"
				  elif [ "$OTHER_COMPONENT_BRANCH" == "Cortx-v1.0.0_Beta"  ]; then
						RPM_COPY_PATH="/mnt/bigstorage/releases/cortx/components/github/Cortx-v1.0.0_Beta/$os_version/dev/"
				  elif [ "$OTHER_COMPONENT_BRANCH" == "cortx-1.0"  ]; then
						RPM_COPY_PATH="/mnt/bigstorage/releases/cortx/components/github/cortx-1.0/$os_version/dev/"		
				  else
						RPM_COPY_PATH="/mnt/bigstorage/releases/cortx/components/github/custom-ci/release/$os_version/dev"
				  fi
					
					if [ "$CSM_BRANCH" == ""Cortx-v1.0.0_Beta"" ]; then
					CUSTOM_COMPONENT_NAME="motr|s3server|hare|cortx-ha|provisioner|csm|sspl"
					else
					CUSTOM_COMPONENT_NAME="motr|s3server|hare|cortx-ha|provisioner|csm-agent|csm-web|sspl"
					fi
					
                    for env in "dev" ;
                    do
						test -d $integration_dir/$release_tag/cortx_iso/ && rm -rf $integration_dir/$release_tag/cortx_iso/
						mkdir -p $integration_dir/$release_tag/cortx_iso/
						pushd $components_dir/$env
							echo $CUSTOM_COMPONENT_NAME
							echo $CUSTOM_COMPONENT_NAME | tr "|" "\n"
							for component in $(echo $CUSTOM_COMPONENT_NAME | tr "|" "\n")
								do
								echo -e "Copying RPM's for $component"
								if ls $component/last_successful/*.rpm 1> /dev/null 2>&1; then
									mv $component/last_successful/*.rpm $integration_dir/$release_tag/cortx_iso/
									rm -rf $(readlink $component/last_successful)
									rm -f $component/last_successful
								else
									echo "Packages not available for $component. Exiting"
								exit 1							   
								fi
							done
						popd


						pushd $RPM_COPY_PATH
						for component in `ls -1 | grep -E -v "$CUSTOM_COMPONENT_NAME" | grep -E -v 'luster|halon|mero|motr|csm|cortx-extension'`
						do
							echo -e "Copying RPM's for $component"
							if ls $component/last_successful/*.rpm 1> /dev/null 2>&1; then
								cp $component/last_successful/*.rpm $integration_dir/$release_tag/cortx_iso/
							else
								echo "Packages not available for $component. Exiting"
							exit 1	
							fi
						done
                    done
                '''
			}
		}

        stage('RPM Validation') {
			steps {
                script { build_stage = env.STAGE_NAME }
				sh label: 'Validate RPMS for Mero Dependency', script:'''
                for env in "dev" ;
                do
                    set +x
                    echo "VALIDATING $env RPM'S................"
                    echo "-------------------------------------"
                    pushd $integration_dir/$release_tag/cortx_iso/
					if [ "${CSM_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${HARE_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${MOTR_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${PRVSNR_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${S3_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${SSPL_BRANCH}" == "Cortx-v1.0.0_Beta" ]; then
						mero_rpm=$(ls -1 | grep "eos-core" | grep -E -v "eos-core-debuginfo|eos-core-devel|eos-core-tests")
					else
						mero_rpm=$(ls -1 | grep "cortx-motr" | grep -E -v "cortx-motr-debuginfo|cortx-motr-devel|cortx-motr-tests")
					fi
                    mero_rpm_release=`rpm -qp ${mero_rpm} --qf '%{RELEASE}' | tr -d '\040\011\012\015'`
                    mero_rpm_version=`rpm -qp ${mero_rpm} --qf '%{VERSION}' | tr -d '\040\011\012\015'`
                    mero_rpm_release_version="${mero_rpm_version}-${mero_rpm_release}"
                    for component in `ls -1`
                    do
						if [ "${CSM_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${HARE_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${MOTR_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${PRVSNR_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${S3_BRANCH}" == "Cortx-v1.0.0_Beta" ] || [ "${SSPL_BRANCH}" == "Cortx-v1.0.0_Beta" ]; then
							 mero_dep=`echo $(rpm -qpR ${component} | grep -E "eos-core = |mero =") | cut -d= -f2 | tr -d '\040\011\012\015'`
						else	 
							mero_dep=`echo $(rpm -qpR ${component} | grep -E "cortx-motr = |mero =") | cut -d= -f2 | tr -d '\040\011\012\015'`
						fi
                        if [ -z "$mero_dep" ]
                        then
                            echo "\033[1;33m $component has no dependency to mero - Validation Success \033[0m "
                        else
                            if [ "$mero_dep" = "$mero_rpm_release_version" ]; then
                                echo "\033[1;32m $component mero version matches with integration mero rpm($mero_rpm_release_version) Good to Go - Validation Success \033[0m "
                            else
                                echo "\033[1;31m $component mero version mismatchs with integration mero rpm($mero_rpm_release_version) - Validation Failed \033[0m"
                                exit 1
                            fi
                        fi
                    done
                done
                '''
			}
		}
		
		stage ('Sign rpm') {
			steps {
                script { build_stage = env.STAGE_NAME }
                
			 checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
                
                sh label: 'Generate Key', script: '''
                    set +x
					pushd scripts/rpm-signing
                    cat gpgoptions >>  ~/.rpmmacros
                    sed -i 's/passphrase/'${passphrase}'/g' genkey-batch
                    gpg --batch --gen-key genkey-batch
                    gpg --export -a 'Seagate'  > RPM-GPG-KEY-Seagate
                    rpm --import RPM-GPG-KEY-Seagate
					popd
				'''
                sh label: 'Sign RPM', script: '''
                    set +x
					pushd scripts/rpm-signing
                    chmod +x rpm-sign.sh
                    cp RPM-GPG-KEY-Seagate $integration_dir/$release_tag/cortx_iso/
                    
                    for rpm in `ls -1 $integration_dir/$release_tag/cortx_iso/*.rpm`
                    do
                    ./rpm-sign.sh ${passphrase} $rpm
                    done
					popd

                '''
			}
		}
		
		stage ('Build MANIFEST') {
			steps {
                script { build_stage = env.STAGE_NAME }

                sh label: 'Build MANIFEST', script: """
					pushd scripts/release_support
                    sh build_release_info.sh $integration_dir/$release_tag/cortx_iso/
					sh build_readme.sh $integration_dir/$release_tag
					popd
					
                    cp $integration_dir/$release_tag/README.txt .
                    cp $integration_dir/$release_tag/cortx_iso/RELEASE.INFO .
                """
			}
		}

		stage ('Repo Creation') {
			steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Repo Creation', script: '''
                    pushd $integration_dir/$release_tag/cortx_iso/
                    rpm -qi createrepo || yum install -y createrepo
                    createrepo .
                    popd
                '''
			}
		}

		
		stage ('Link 3rd_party and python_deps') {
			steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Tag Release', script: '''
                    pushd $release_dir/github/integration-custom-ci/release/$os_version/$release_tag
							ln -s $thrid_party_dir 3rd_party
							ln -s $python_deps python_deps
                    popd
                '''
			}
		}

		stage ('Generate ISO Image') {
		    steps {
				
				sh label: 'Generate Single ISO Image', script:'''
		        mkdir $integration_dir/$release_tag/iso && pushd $integration_dir/$release_tag/iso
					genisoimage -input-charset iso8859-1 -f -J -joliet-long -r -allow-lowercase -allow-multidot -publisher Seagate -o $release_tag-single.iso $integration_dir/$release_tag/
				popd
				'''
				sh label: 'Generate ISO Image', script:'''
		         pushd $integration_dir/$release_tag/iso
					genisoimage -input-charset iso8859-1 -f -J -joliet-long -r -allow-lowercase -allow-multidot -publisher Seagate -o $release_tag.iso $integration_dir/$release_tag/cortx_iso/
				popd
				'''			

				sh label: 'Print Release Build and ISO location', script:'''
				echo "Custom Release Build and ISO is available at,"
					echo "http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/release/$os_version/$release_tag/"
					echo "http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/release/$os_version/$release_tag/iso/$release_tag.iso"
					echo "http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/release/$os_version/$release_tag/iso/$release_tag-single.iso"
		        '''
		    }
		}
	}
	
	post {
	
		always {
            script {
			
                env.release_build_location = "http://cortx-storage.colo.seagate.com/releases/cortx/github/integration-custom-ci/release/${env.os_version}/${env.release_tag}"
                env.release_build = "${env.release_tag}"
				def recipientProvidersClass = [[$class: 'RequesterRecipientProvider']]
                
                def mailRecipients = "shailesh.vaidya@seagate.com"
                emailext ( 
                    body: '''${SCRIPT, template="release-email.template"}''',
                    mimeType: 'text/html',
                    subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                    attachLog: true,
                    to: "${mailRecipients}",
					recipientProviders: recipientProvidersClass
                )
            }
        }
    }
}