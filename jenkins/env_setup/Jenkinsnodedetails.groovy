def count = 0
try {
    for (Node n : Jenkins.get().getNodes()) {
        try {
            if (n.getComputer().isOffline() == true) {
                count ++
                hudson.plugins.jobConfigHistory.ConfigInfo config = n.getComputer().actions[0].getSlaveConfigs()[0]
                println('Sr.no:' + count + ' Node_name:' + n.name + ' Host:' + n.launcher.host + ' Label:' + n.getLabelString() + ' User:' + config.getUser() + ' UserId:' + config.getUserID())
                println('-------------')
            }
        }
        catch (Exception e) {
            println('Inside catch of Sr.no: ' + count + ' ' + e)
            println('-------------')
        }
    }
}
catch (Exception e) {
    println('Inside catch 2. ' + e)
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
                sh 'curl -k -u $USER:$PASS -o $WORKSPACE/console.log "https://eos-jenkins.colo.seagate.com/job/Cortx-Automation/job/Reports/job/offline-node-report/lastBuild/consoleText"'
           }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'console.log', followSymlinks: false, fingerprint: true, onlyIfSuccessful: true
        }
    }
}
