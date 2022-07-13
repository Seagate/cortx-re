pipeline {
    agent {
        node {
            label 'docker-k8-deployment-node'
        }
    }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }

    environment {

    }

    parameters {
    }    

    stages {

    }

    post {
        always {
            cleanWs()
        }
    }
}