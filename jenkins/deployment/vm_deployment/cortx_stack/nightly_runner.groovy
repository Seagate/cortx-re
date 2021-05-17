#!/usr/bin/env groovy
pipeline {
	 	 
    agent {
		node {
			label 'docker-cp-centos-7.8.2003-node'
		}
	}
	
    options {
	timeout(time: 60, unit: 'MINUTES')
        ansiColor('xterm') 
	}
	
    triggers {
        cron('30 2 * * *')
	}
    stages {
	
	stage('Nightly Jobs') {
            parallel {
	      stage('Stable') {
        	// the below jobs run parallel
		steps {
			echo "Trigger 3 node Stable Nightly job"
			build(job: "Release_Engineering/Nightly_Run/R2-3Node-Stable-Nightly", wait: false, parameters: [string(name: 'CORTX_BUILD', value: 'http://cortx-storage.colo.seagate.com/releases/cortx/github/stable/centos-7.8.2003/last_successful_prod/')])
			echo "Trigger 3 node Main Nightly job"
			build(job: "Release_Engineering/Nightly_Run/R2-3Node-Main-Nightly", wait: false, parameters: [string(name: 'CORTX_BUILD', value: 'http://cortx-storage.colo.seagate.com/releases/cortx/github/main/centos-7.8.2003/last_successful_prod/')])
		        }
	        }    
	    }	
        }
    }
}
