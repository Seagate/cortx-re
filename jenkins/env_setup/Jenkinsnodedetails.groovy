for (Node n : Jenkins.get().getNodes()) {
	for (hudson.plugins.jobConfigHistory.ComputerConfigHistoryAction m : n.getComputer().actions) {
    		for(hudson.plugins.jobConfigHistory.ConfigInfo config : m.getAgentConfigs()) {
        		println('Slave:' + n.name + ' User:'+ config.getUser() + ' UserId:'+ config.getUserID());
   		 }
	}
println('Host:'+ n.launcher.host + ' Label:' + n.getLabelString() + ' Computer.isOffline:' + n.getComputer().isOffline()) ;
println "--------------------------------"
}
//Have to make changes accordingly as Jenkins Production
/*
pipeline{
    agent { label 'master'}
    stages{
        stage('Gettinglogfile')
        {  
           steps
           {
                sh 'curl -u user2:user2@123 -o $WORKSPACE/console.log "http://ssc-vm-g4-rhev4-1690.colo.seagate.com:8080/job/Finduserdetails/lastBuild/consoleText"'
           } 
        }        
    }
} */
