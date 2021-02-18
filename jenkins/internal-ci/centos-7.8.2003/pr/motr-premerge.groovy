pipeline {

    agent {
        node {
            label 'motr-remote-controller-hw'
        }
    }

    parameters {
        string(name: 'MOTR_PR', defaultValue: 'https://github.com/gowthamchinna/cortx-motr/pull/1', description: 'MOTR PR URL (HTTPS)')
    }

    options {
		timestamps ()
        timeout(time: 10, unit: 'HOURS')
	}

    environment{

        MOTR_PR = "${ghprbPullLink != null ? ghprbPullLink : MOTR_PR}"

        PULL_ID = "${MOTR_PR}".substring("${MOTR_PR}".lastIndexOf('/') + 1);
        GITHUB_PR_API_URL = "https://api.github.com/repos/gowthamchinna/cortx-motr/pulls/${PULL_ID}"
        SOURCE_REPO = getRepoInfo(GITHUB_PR_API_URL, ".head.repo.full_name")
        BASE_REPO = getRepoInfo(GITHUB_PR_API_URL, ".base.repo.full_name")  

        REPO_BRANCH = getRepoInfo(GITHUB_PR_API_URL, ".head.ref")
        REPO_URL = "https://github.com/${SOURCE_REPO}"
    }

	stages {

        stage('Trigger VM Test'){
            when { expression { false } }
            steps{
                build job: '../Motr/Motr-PR-Test-VM', propagate: false, wait: false, parameters: [string(name: 'MOTR_PR', value: "${MOTR_PR}")]
            }
        }

        stage('Checkout') {
            when { expression { true } }
            steps {
                cleanWs()
                dir('motr'){
                    checkout([$class: 'GitSCM', branches: [[name: "*/${REPO_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog'], [$class: 'SubmoduleOption', disableSubmodules: false, parentCredentials: true, recursiveSubmodules: true, reference: '', trackingSubmodules: false]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${REPO_URL}"]]])
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
        stage('Static Code Analysis') {
            when { expression { true } }
            steps {
                script {

                    sh  label: 'run cppcheck', script:'''
                        mkdir -p html
                        /opt/perlbrew/perls/perl-5.22.0/bin/perl seagate-eng-tools/scripts/build_support/cppcheck.pl --src=motr  --debug --maxconfig=2 --jenkins --xmlreport=diff.xml --cfgfile=seagate-eng-tools/jenkins/build/motr_cppcheck.yaml  --htmldir=html --reporturl="${BUILD_URL}/CppCheck_Report/"
                    '''
                    sh  label: 'get cppcheck result', script:'''
                        no_warning=$(cat html/index.html | grep "total<br/><br/>" | tr -dc '0-9')
                        sca_result="Cppcheck: No new warnings found"
                        if [ "$no_warning" != "0" ]; then 
                            sca_result="Cppcheck: Found $no_warning new warning(s)"
                        fi
                        echo $sca_result > cppcheck_Result
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'html/*.*', fingerprint: true
                    publishHTML([allowMissing: false, alwaysLinkToLastBuild: false, keepAll: true, reportDir: 'html', reportFiles: 'index.html', reportName: 'CppCheck Report', reportTitles: ''])
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
            post {
                always {
                    script {
                        archiveArtifacts artifacts: 'workdir/**/*.*, build*.*, artifacts/*.*', fingerprint: true, allowEmptyArchive: true
                        summary = junit testResults: '**/junit/*.junit', allowEmptyResults : true, testDataPublishers: [[$class: 'AttachmentPublisher']]     

                        try {
                            postGithubComment(summary, readFile('cppcheck_Result').trim())
                        } catch (err) {
                            echo "Failed to update Github PR Comment ${err}"
                        }
                        cleanWs()
                    }                            
                }
            }
        }    	
    }
}

@NonCPS
def postGithubComment(junitSummary, cppcheckSummary) {
    def githubComment="<h1>Jenkins CI Result : <a href=\'${BUILD_URL}\'>${env.JOB_BASE_NAME}#${env.BUILD_ID}</a></h1>"
    hudson.tasks.test.AbstractTestResultAction testResultAction = currentBuild.rawBuild.getAction(hudson.tasks.test.AbstractTestResultAction.class)
    if (testResultAction != null) {
        def testURL = "${BUILD_URL}/testReport/junit"
        def passedTest = testResultAction.getPassedTests().collect { "<a href=\'${testURL}/${it.className.replaceAll('\\.','/')}\' target='_blank'>${it.className.replaceAll('\\.','/')}</a>" }.join("</br>")
        def failedTest = testResultAction.getFailedTests().collect { "<a href=\'${testURL}/${it.className.replaceAll('\\.','/')}\' target='_blank'>${it.className.replaceAll('\\.','/')}</a>"  }.join("</br>")
        def skippedTest = testResultAction.getSkippedTests().collect { "<a href=\'${testURL}/${it.className.replaceAll('\\.','/')}\' target='_blank'>${it.className.replaceAll('\\.','/')}</a>" }.join("</br>")
      
        githubComment+="<h2>Motr Test Summary</h2><table><tr><th>Test Result</th><th>Count</th><th>Info</tr>"
        githubComment+="<tr><td>:x:Failed</td><td>${junitSummary.failCount}</td><td><details><summary>:file_folder:</summary><p>${failedTest}</p></details></td></tr>"
        githubComment+="<tr><td>:checkered_flag:Skipped</td><td>${junitSummary.skipCount}</td><td><details><summary>:file_folder:</summary><p>${skippedTest}</p></details></td></tr>"
        githubComment+="<tr><td>:heavy_check_mark:Passed</td><td>${junitSummary.passCount}</td><td><details><summary>:file_folder:</summary><p>${passedTest}</p></details></td></tr>"
        githubComment+="<tr><td>Total</td><td>${junitSummary.totalCount}</td><td><a href=\'${testURL}\'>:link:</a></td></tr></table>"

        if(cppcheckSummary){
            githubComment+="<h2> CppCheck Summary</h2><p> &emsp;&emsp;&emsp;${cppcheckSummary} :thumbsup:</p>"
        }
        githubAPIAddComments(githubComment)
    }
}

// method to call gitub post comment api to add comments
def githubAPIAddComments(text_pr){

    withCredentials([usernamePassword(credentialsId: "cortx-admin-github", passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'USER')]) {
        sh """
            curl -s -H \"Authorization: token ${GITHUB_TOKEN}\" -X POST -d '{"body":"${text_pr}"}' \"https://api.github.com/repos/${BASE_REPO}/issues/${PULL_ID}/comments\"
        """
    }
}

// Get PR info from github api
def getRepoInfo(api, data_path){

    withCredentials([usernamePassword(credentialsId: "cortx-admin-github", passwordVariable: 'GITHUB_TOKEN', usernameVariable: 'USER')]) {
        result = sh(script: """ curl -s  -H "Authorization: token ${GITHUB_TOKEN}" "${api}" | jq -r "${data_path}" """, returnStdout: true).trim()
    }
    return result 
}