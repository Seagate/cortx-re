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
                  node --version
              '''
          }
      }
      stage('Checkout repositories') {
          steps {
              // cleanWs()
              script {
                  def projects = readJSON file: "${env.WORKSPACE}/config.json"
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
                def projects = readJSON file: "${env.WORKSPACE}/config.json"
				def scan_list = projects.global_scope
                projects.repository.each { entry ->
                    dir(entry.name) {
						def file = '../' + entry.name + '.alex'
						def file_custom = '../' + entry.name + '.custom'
						def words = entry.local_scope
						sh ''' 
						  set +e
						  cp ../alexignore .alexignore
						  cp ../alexrc .alexrc
						  #echo 'CORVault, lyve cloud.' >foo.txt
						  repo_list=`echo '''+ words +'''`
						  scan_words=`echo '''+ scan_list +'''`
						  
						  repo_list=${repo_list//,/\\|}
						  scan_words=${scan_words//,/\\|}
						  grep -rnwo '.' -e \"$repo_list\" >> '''+ file_custom +'''
						  grep -rnwo '.' -e \"$scan_words\" >> '''+ file_custom +'''
						  alex . >> ''' + file +''' 2>&1
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
                    useEmailList = 'shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
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
                    useEmailList = 'shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
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
      stage('Send Email to S3') {
          steps {
              script {
                  def useEmailList = ''
                  if ( params.environment == 'prod') {
                    useEmailList = 'shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                  }
                  env.ForEmailPlugin = env.WORKSPACE
                  emailext mimeType: 'text/html',
                  body: '${FILE, path="cortx-s3server.html"}',
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
                    useEmailList = 'shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
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
                    useEmailList = 'shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
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
      stage('Send Email to Management Portal') {
          steps {
              script {
                  def useEmailList = ''
                  if ( params.environment == 'prod') {
                    useEmailList = 'shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                  }
                  env.ForEmailPlugin = env.WORKSPACE
                  emailext mimeType: 'text/html',
                  body: '${FILE, path="cortx-management-portal.html"}',
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
                    useEmailList = 'shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
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
      stage('Send Email to Monitor') {
          steps {
              script {
                  def useEmailList = ''
                  if ( params.environment == 'prod') {
                    useEmailList = 'shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                  }
                  env.ForEmailPlugin = env.WORKSPACE
                  emailext mimeType: 'text/html',
                  body: '${FILE, path="cortx-monitor.html"}',
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
                      useEmailList = 'shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
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
                      useEmailList = 'shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
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
        stage('Send Email to Posix') {
            steps {
                script {
                    def useEmailList = ''
                    if ( params.environment == 'prod') {
                      useEmailList = 'shailesh.vaidya@seagate.com, priyank.p.dalal@seagate.com, john.bent@seagate.com, venkatesh.k@seagate.com'
                    }
                    env.ForEmailPlugin = env.WORKSPACE
                    emailext mimeType: 'text/html',
                    body: '${FILE, path="cortx-posix.html"}',
                    subject: 'Alex Scan Report - [ Date :' +new Date().format("dd-MMM-yyyy") + ' ]',
                    recipientProviders: [[$class: 'RequesterRecipientProvider']],
                    to: useEmailList
                }
            }
        }
    }
}
