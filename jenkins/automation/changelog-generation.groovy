pipeline {
    agent {
        node {
            label 'docker-image-builder-centos-7.9.2009'
        }
    }
    
    options {
        timeout(time: 60, unit: 'MINUTES')
        ansiColor('xterm') 
    }
    
    parameters {
        string(name: 'BUILD_FROM', defaultValue: '', description: '''Provide full build url till RELEASE.INFO like below OR Provide docker image name like below.
 <h6> e.g. http://cortx-storage.colo.seagate.com/releases/cortx/github/main/rockylinux-8.4/5477/dev/RELEASE.INFO </h6><br>OR<br><h6>cortx-docker.colo.seagate.com/seagate/cortx-<component>:2.0.0-27-motr-pr</h6>''')
        string(name: 'BUILD_TO', defaultValue: '', description: '''In BUILD_FROM parameter if you provided build full url then here also provide build full url only, not docker image or not build number. You should follow same for docker image & build number as well. Same type of parameter should be provide in BUILD_FROM and BUILD_TO parameters.
 <h6> e.g. http://cortx-storage.colo.seagate.com/releases/cortx/github/main/rockylinux-8.4/5478/dev/RELEASE.INFO </h6><br>OR<br><h6>cortx-docker.colo.seagate.com/seagate/cortx-<component>:2.0.0-27-motr-pr</h6>''')
    }

    stages {    
        stage ('Checkout Script') {
            steps {
                checkout([$class: 'GitSCM', branches: [[name: 'main']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
            }
        }
        
        stage ('Build MANIFEST') {
            steps {
                withCredentials([string(credentialsId: 'shailesh-github-token', variable: 'ACCESS_TOKEN')]) {
                    sh label: 'Build MANIFEST', script: """
                        sh -x scripts/release_support/changelog.sh ${BUILD_FROM} ${BUILD_TO}
                        cp /root/git_build_checkin_stats/clone/git-build-checkin-report.txt CHANGESET.txt 
                    """
                }   
            }
        }
        
    }

    post {
        always {
            script {
                archiveArtifacts artifacts: "CHANGESET.txt", onlyIfSuccessful: false, allowEmptyArchive: true
            }
        }
    }
}