pipeline {
    agent {
          node {
             label "${j_agent}"
          }
      }
      parameters {
          string(
              defaultValue: 'centos-7.9.2009',
              name: 'j_agent',
              description: 'Jenkins Agent',
              trim: true
          )
      string(
              defaultValue: 'dev',
              name: 'environment',
              description: 'Environment',
              trim: true
          )
      }
      
      triggers { 
          cron('0 0 1-7 * 1') 
      }

      stages {
      stage('Checkout source code') {
              steps {
                  script {
                      checkout([$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/seagate/cortx-re/']]])
                      sh 'cp ./scripts/automation/alex/* .'
                  }
              }
          }

      stage('Install Dependencies') {
          steps {
              sh label: 'Installed Dependencies', script: '''
                  yum install epel-release -y
                  curl -sL https://rpm.nodesource.com/setup_12.x | sudo bash -
                  yum install nodejs -y
                  npm install alex --global -y
                  virtualenv alex --python=python2.7
                  source ./alex/bin/activate
                  pip install GitPython==2.1.15
                  NODE_OPTIONS="--max-old-space-size=1536"
                  
              '''
          }
      }
      stage('Checkout repositories') {
          steps {
              // cleanWs()
              script {
                  sh """ rm -f """ + "${env.WORKSPACE}/cortx-*.html"
                  sh """ rm -f """ + "${env.WORKSPACE}/cortx-*.alex"
                  sh """ rm -f """ + "${env.WORKSPACE}/cortx-*.custom"
                  def projects = readJSON file: "/root/config.json"
                  projects.repository.each { entry ->
                    echo entry.name
                    def repourl = 'https://github.com/seagate/' + entry.name
                    stage ('Checkout Repo:' + entry.name) {
                        dir (entry.name) {
                            timestamps {
                              checkout([$class: 'GitSCM', branches: [[name: "main"]], doGenerateSubmoduleConfigurations: false, userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: repourl]]])
                            }
                        }
                    }
                  }
              }
          }
      }
      stage('Run Alex') {
          steps {
              script {
                
                def projects = readJSON file: "/root/config.json"
				def scan_list = projects.global_scope
                projects.repository.each { entry ->
                    dir(entry.name) {
						def file = '../' + entry.name + '.alex'
						def file_custom = '../' + entry.name + '.custom'
						def words = entry.local_scope
						sh ''' 
						  set +e
						  cp ../alexignore .alexignore
						  #cp /root/alexrc .alexrc
						  #echo 'CORVault, lyve cloud.' >foo.txt
						  repo_list=`echo '''+ words +'''`
						  scan_words=`echo '''+ scan_list +'''`
						  
						  repo_list=${repo_list//,/\\\\|}
						  scan_words=${scan_words//,/\\\\|}
						  grep -rnwo '.' -e \"$repo_list\" >> '''+ file_custom +'''
						  grep -rnwo '.' -e \"$scan_words\" >> '''+ file_custom +'''
						  alex . >> ''' + file +''' 2>&1
						  cat ''' + file_custom + '''
						  set -e
						'''
					}
                  }
              }
          }	
	  }
      stage('Generate HTML report') {
          steps {
              script {
                echo "Get the scanned alex file."
                sh "ls *.alex > listAlexFiles"
                def files = readFile( "listAlexFiles" ).split( "\\r?\\n" );
                files.each { item ->
                   sh ''' cat ''' + item
                   sh ''' python alex.py -f ''' + item 
                }
              }
          }
      }
      stage('Send Email to Motr') {
          steps {
              script {
                  def useEmailList = ''
                  if ( params.environment == 'prod') {
                    useEmailList = 'chandradhar.raval@seagate.com, bhargav.dekivadiya@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                  }
                  env.ForEmailPlugin = env.WORKSPACE
                  emailext mimeType: 'text/html',
                   body: '${FILE, path="cortx-motr.html"}',
                   subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                   recipientProviders: [[$class: 'RequesterRecipientProvider']],
                   to: useEmailList

               }
          }
      }
      stage('Send Email to RE') {
          steps {
              script {
                  def useEmailList = ''
                  if ( params.environment == 'prod') {
                    useEmailList = 'shailesh.vaidya@seagate.com, mukul.malhotra@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                  }
                  env.ForEmailPlugin = env.WORKSPACE
                  emailext mimeType: 'text/html',
                  body: '${FILE, path="cortx-re.html"}',
                  subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                  recipientProviders: [[$class: 'RequesterRecipientProvider']],
                  to: useEmailList
              }
          }
      }
      stage('Send Email to HA') {
          steps {
              script {
                  def useEmailList = ''
                  if ( params.environment == 'prod') {
                    useEmailList = 'archana.limaye@seagate.com, ajay.paratmandali@seagate.com, indrajit.zagade@seagate.com, ajay.srivastava@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                  }
                  env.ForEmailPlugin = env.WORKSPACE
                  emailext mimeType: 'text/html',
                  body: '${FILE, path="cortx-ha.html"}',
                  subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                  recipientProviders: [[$class: 'RequesterRecipientProvider']],
                  to: useEmailList
              }
          }
      }
      stage('Send Email to Hare') {
          steps {
              script {
                  def useEmailList = ''
                  if ( params.environment == 'prod') {
                    useEmailList = 'mandar.sawant@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                  }
                  env.ForEmailPlugin = env.WORKSPACE
                  emailext mimeType: 'text/html',
                  body: '${FILE, path="cortx-hare.html"}',
                  subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                  recipientProviders: [[$class: 'RequesterRecipientProvider']],
                  to: useEmailList
              }
          }
      }
      
      stage('Send Email to Manager') {
          steps {
              script {
                  def useEmailList = ''
                  if ( params.environment == 'prod') {
                    useEmailList = 'ajay.srivastava@seagate.com, soniya.moholkar@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                  }
                  env.ForEmailPlugin = env.WORKSPACE
                  emailext mimeType: 'text/html',
                  body: '${FILE, path="cortx-manager.html"}',
                  subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                  recipientProviders: [[$class: 'RequesterRecipientProvider']],
                  to: useEmailList
              }
          }
      }
      
      stage('Send Email to Provisioner') {
            steps {
                script {
                    def useEmailList = ''
                    if ( params.environment == 'prod') {
                      useEmailList = 'nitin.nimran@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                    }
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="cortx-prvsnr.html"}',
                    subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
                    to: useEmailList
                }
            }
        }
      stage('Send Email to Utils') {
            steps {
                script {
                    def useEmailList = ''
                    if ( params.environment == 'prod') {
                      useEmailList = 'sachin.punadikar@seagate.com, ajay.srivastava@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                    }
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="cortx-utils.html"}',
                    subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
                    to: useEmailList
                }
            }
        }
		stage('Send Email to RGW Integration') {
            steps {
                script {
                    def useEmailList = ''
                    if ( params.environment == 'prod') {
                      useEmailList = 'rahul.tripathi@seagate.com, sachitanand.shelake@seagate.com,chetan.deshmukh@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                    }
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="cortx-rgw-integration.html"}',
                    subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
                    to: useEmailList
                }
            }
        }
		stage('Send Email to K8s') {
            steps {
                script {
                    def useEmailList = ''
                    if ( params.environment == 'prod') {
                      useEmailList = 'walter.lopatka@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                    }
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="cortx-k8s.html"}',
                    subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
                    to: useEmailList
                }
            }
        }
		stage('Send Email to Motr Apps') {
            steps {
                script {
                    def useEmailList = ''
                    if ( params.environment == 'prod') {
                      useEmailList = 'andriy.tkachuk@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                    }
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="cortx-motr-apps.html"}',
                    subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
                    to: useEmailList
                }
            }
        }
		stage('Send Email to Multisite') {
            steps {
                script {
                    def useEmailList = ''
                    if ( params.environment == 'prod') {
                      useEmailList = 'mehmet.balman@seagate.com, sachin.punadikar@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                    }
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="cortx-multisite.html"}',
                    subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
                    to: useEmailList
                }
            }
        }
		stage('Send Email to Cortx Parent') {
            steps {
                script {
                    def useEmailList = ''
                    if ( params.environment == 'prod') {
                      useEmailList = 'john.bent@seagate.com, venkatesh.k@seagate.com'
                    }
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="cortx.html"}',
                    subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
                    to: useEmailList
                }
            }
        }
		stage('Send Email to cortx-test') {
            steps {
                script {
                    def useEmailList = ''
                    if ( params.environment == 'prod') {
                      useEmailList = 'deepak.choudhary@seagate.com, sining.wu@seagate.com, ganesan.umanesan@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                    }
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="cortx-test.html"}',
                    subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
                    to: useEmailList
                }
            }
        }
		stage('Send Email to Mio') {
            steps {
                script {
                    def useEmailList = ''
                    if ( params.environment == 'prod') {
                      useEmailList = 'sining.wu@seagate.com, sai.narasimhamurthy@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                    }
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="cortx-mio.html"}',
                    subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
                    to: useEmailList
                }
            }
        }
		stage('Send Email to Motr-Galois') {
            steps {
                script {
                    def useEmailList = ''
                    if ( params.environment == 'prod') {
                      useEmailList = 'chandradhar.raval@seagate.com, bhargav.dekivadiya@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                    }
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="cortx-motr-galois.html"}',
                    subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
                    to: useEmailList
                }
            }
        }
        stage('Generate Pickle Object') {
          steps {
              script {
                echo "Prepare the pickle object"
                   sh ''' export ALEX_PATH=/mnt/bigstorage/releases/opensource_builds/alex_report && python alex_pickle.py'''
              }
          }
        }
        stage('Git Pickle Push') {
            steps{
                script{
                    withCredentials([usernamePassword(credentialsId: 'cortx-admin-github', usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
						sh '''
							pushd ./cortx/metrics/pickles
							git add alex.pickle
							git commit -m "Update the Alex pickle"
							git push https://${USERNAME}:${PASSWORD}@github.com/seagate/cortx.git HEAD:main
						'''
					}
                }
                    
            }
        }
    }
    
}
