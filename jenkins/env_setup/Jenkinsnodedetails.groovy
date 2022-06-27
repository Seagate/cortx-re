def count = 0
try {
    this.println('Sr.no,Node_name,Host,Label,User,UserId')
    for (Node n : Jenkins.get().getNodes()) {
        try {
            if (n.getComputer().isOffline() == true) {
                count ++
                hudson.plugins.jobConfigHistory.ConfigInfo config = n.getComputer().actions[0].getSlaveConfigs()[0]
                this.println('' + count + ',' + n.name + ',' + n.launcher.host + ',' + n.getLabelString() + ',' + config.getUser() + ',' + config.getUserID())
            }
        }
        catch (Exception e) {
            this.println('' + count + ' ' + e)
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
                sh 'curl -k -u $USER:$PASS -o $WORKSPACE/inbetween-offline-nodes.txt "https://eos-jenkins.colo.seagate.com/job/Cortx-Automation/job/Reports/job/offline-node-report/lastBuild/consoleText"'
           }
        }
            stage('Parsing log file')
            {
                steps
                {
                    sh 'awk "BEGIN{printf "Sr  Node_name   Host    Label   User    UserId\n"} /^[1-9]/{print}" $WORKSPACE/inbetween-offline-nodes.txt > $WORKSPACE/offline-nodes.txt'
                }
            }
    }
    post {
        always {
            archiveArtifacts artifacts: 'offline-nodes.txt', followSymlinks: false, fingerprint: true, onlyIfSuccessful: true
        }
    }
}
