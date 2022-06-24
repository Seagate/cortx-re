def count = 0
try {
    println('Sr.no,Node_name,Host,Label,User,UserId')
    for (Node n : Jenkins.get().getNodes()) {
        try {
            if (n.getComputer().isOffline() == true) {
                count ++
                hudson.plugins.jobConfigHistory.ConfigInfo config = n.getComputer().actions[0].getSlaveConfigs()[0]
                println('' + count + ',' + n.name + ',' + n.launcher.host + ',' + n.getLabelString() + ',' + config.getUser() + ',' + config.getUserID())
            }
        }
        catch (Exception e) {
            println('Inside catch of Sr.no: ' + count + ' ' + e)
        }
    }
}
catch (Exception e) {
    this.println('Inside catch 2. ' + e)
}
pipeline {
    agent any
    environment {
        USER = credentials('offline-node-report-user')
        PASS = credentials('offline-node-report-pass')
    }
    stages {
            stage('Gettinglogfile')
        {
                steps
           {
                sh 'curl -k -u $USER:$PASS -o $WORKSPACE/offline-nodes.txt "https://eos-jenkins.colo.seagate.com/job/Cortx-Automation/job/Reports/job/offline-node-report/lastBuild/consoleText"'
           }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'offline-nodes.txt', followSymlinks: false, fingerprint: true, onlyIfSuccessful: true
        }
    }
}
