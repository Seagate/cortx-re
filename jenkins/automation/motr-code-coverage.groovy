pipeline {

	agent {
		node {
			label 'motr-remote-code-coverage-vm'
		}
	}
	    	
	options {
		timestamps ()
		timeout(time: 15, unit: 'HOURS')
	}

	environment{
		SSC_AUTH = credentials("${SSC_AUTH_ID}")
	}

	stages {
		stage('Checkout') {
			when { expression { true } }
			steps {
				cleanWs()
			        dir('motr'){
					git credentialsId: 'cortx-admin-github', url: "https://github.com/Seagate/cortx-motr.git", branch: "main"
				}
				script{
					sh '''
						WD=$(pwd)
						export MOTR="${WD}/motr"
						(cd ${MOTR}/.xperior/testds/ && curl -O https://raw.githubusercontent.com/Seagate/cortx-motr/code_coverage/.xperior/testds/motr-single_tests.yaml)
						(cd ${MOTR}/scripts/jenkins/ && curl -O https://raw.githubusercontent.com/Seagate/cortx-motr/code_coverage/scripts/jenkins/build)
					'''
				}
				dir('xperior'){
					git credentialsId: 'cortx-admin-github', url: "https://github.com/Seagate/xperior.git", branch: "main"
				}
				dir('xperior-perl-libs'){
					git credentialsId: 'cortx-admin-github', url: "https://github.com/Seagate/xperior-perl-libs.git", branch: "main"
				}
				dir('seagate-ci'){
					git credentialsId: 'cortx-admin-github', url: "https://github.com/Seagate/seagate-ci", branch: "main"
				}
				dir('seagate-eng-tools'){
					git credentialsId: 'cortx-admin-github', url: "https://github.com/Seagate/seagate-eng-tools.git", branch: "main"
				}
			}
		}

		stage('Run Test') {
			when { expression { true } }
			steps {
				script{
					sh '''
						set -ae
						set
						WD=$(pwd)
						hostname
						id
						ls
						export DO_MOTR_BUILD=yes
						export MOTR_REFSPEC=""
						export TESTDIR=motr/.xperior/testds/
						export XPERIOR="${WD}/xperior"
						export ITD="${WD}/seagate-ci/xperior"
						export XPLIB="${WD}/xperior-perl-libs/extlib/lib/perl5"
						export PERL5LIB="${XPERIOR}/mongo/lib:${XPERIOR}/lib:${ITD}/lib:${XPLIB}/"
						export PERL_HOME="/opt/perlbrew/perls/perl-5.22.0/"
						export PATH="${PERL_HOME}/bin/:$PATH:/sbin/:/usr/sbin/"
						export RWORKDIR='motr/motr_test_github_workdir/workdir'
						export IPMIDRV=lan
						export BUILDDIR="/root/${RWORKDIR}"
						export XPEXCLUDELIST=""
						export UPLOADTOBOARD=
						export PRERUN_REBOOT=yes
						${PERL_HOME}/bin/perl "${ITD}/contrib/run_motr_single_test.pl"
					'''
				}
			}
		}
	}
}

node('motr-remote-code-coverage-vm') {
	def remote = [:]
	remote.name = 'test'
	remote.host = "${CNODE}"
	remote.user = 'root'
	remote.password = 'seagate'
	remote.allowAnyHosts = true

	stage('Generate Code Coverage') {
		sshCommand remote: remote, command: "(cd /root/motr/motr_test_github_workdir/workdir/src && ./scripts/coverage/gcov-gen-html user ./ ./)"
	}
	stage('Upload Code Coverage') {
		withCredentials([string(credentialsId: 'MOTR_CODACY_TOKEN', variable: 'TOKEN')]) {
			sshCommand remote: remote, command: "export CODACY_PROJECT_TOKEN=${TOKEN};(cd /root/motr/motr_test_github_workdir/workdir/src && lcov --remove app.info -o lcov.info ; bash <(curl -Ls https://coverage.codacy.com/get.sh) report -r lcov.info)"
		}
	}
}
