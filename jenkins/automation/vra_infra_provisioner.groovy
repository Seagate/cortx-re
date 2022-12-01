pipeline {
    agent {
        node {
            label 'terraform-node'
        }
    }
    
    options {
        timeout(time: 30, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(daysToKeepStr: '30', numToKeepStr: '30'))
        ansiColor('xterm')
    }


    parameters {
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for VRA infra provisioner script', trim: true)
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for VRA infra provisioner script', trim: true)
        string(name: 'VRA_USERNAME', defaultValue: '', description: 'User ID used to login VRA console', trim: true)
        password(name: 'VRA_PASSWORD', description: 'Password used to login VRA console')
        string(name: 'VM_NAMES', defaultValue: '', description: 'list of VM names need to be procured. (comma separated list of VM names)', trim: true)
        choice(
            name: 'VM_CPU',
            choices: ['4', '2', '6', '8'],
            description: 'Number of vCPU required for VM'
        )
        choice(
            name: 'VM_MEMORY',
            choices: ['4096', '2048', '8192', '16384'],
            description: 'Memory required for VM'
        )
        choice(
            name: 'VM_DISKCOUNT',
            choices: ['4', '1', '2', '3', '5', '6', '8', '9', '10'],
            description: 'Number of Disks required for VM'
        )
        choice(
            name: 'VM_DISKSIZE',
            choices: ['50', '25', '100', '150', '200', '250', '300'],
            description: 'Memory required for disks (In GB)'
        )
    }    

    stages {

        stage('Checkout Repository') {
            steps { 
                cleanWs()            
                script {
                    checkout([$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[url: "${CORTX_RE_REPO}"]]])                
                }
            }
        }

        stage('Configure Terraform Environment') {
            steps {             
                script { build_stage = env.STAGE_NAME }
                sh label: '', script: '''
                    pushd scripts/vRealize/
                        export VRA_USERNAME=${VRA_USERNAME}
                        export VRA_PASSWORD=${VRA_PASSWORD}
                        export VM_NAMES=${VM_NAMES}
                        export VM_CPU=${VM_CPU}
                        export VM_MEMORY=${VM_MEMORY}
                        export VM_DISKCOUNT=${VM_DISKCOUNT}
                        export VM_DISKSIZE=${VM_DISKSIZE}
                        ./vra_infrastructure_provisioner.sh --config
                    popd
                '''    
            }
        }

        stage ('Validate Infrastructure Configuration') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: '', script: '''
                    pushd scripts/vRealize/
                        ./vra_infrastructure_provisioner.sh --validate
                    popd
                '''
            }
        }

        stage ('Provision Infrastructure') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: '', script: '''
                    pushd scripts/vRealize/
                        ./vra_infrastructure_provisioner.sh --provision-resources
                    popd
                '''
            }
        }
    }
}
