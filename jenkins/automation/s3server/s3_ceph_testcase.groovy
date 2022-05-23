pipeline {     
    agent {
        node {
            // Agent created with 4GB ram/16GB memory in EOS_SVC_RE1 account 
            label "docker-k8-deployment-node"
            // Use custom workspace for easy troublshooting
            customWorkspace "/root/compatability-test/${INTEGRATION_TYPE}"
        }
    }

    options {
        timeout(time: 240, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm') 
        buildDiscarder(logRotator(daysToKeepStr: '5', numToKeepStr: '5')) 
    }
    parameters {
        string(name: 'CORTX_RE_BRANCH', defaultValue: 'main', description: 'Branch or GitHash for Cluster Setup scripts', trim: true)
        string(name: 'CORTX_RE_REPO', defaultValue: 'https://github.com/Seagate/cortx-re', description: 'Repository for Cluster Setup scripts', trim: true)
        string(name: 'RGW_PORT', defaultValue: '30080', description: 's3-test rgw port', trim: true)
        string(name: 'RGW_MASTER_NODE', defaultValue: '', description: 's3-test rgw master node', trim: true)
        choice(name: 'INTEGRATION_TYPE', choices: [ "ceph"], description: 'S3 Integration Type')
        // Please configure S3_TEST_REPO and S3_TEST_REPO_REV as input parameters manually in Jenkins configuration.
    }

    environment {
        // This config file used for ceph compatibility tests
        S3_TEST_CONF_FILE = "${INTEGRATION_TYPE}_${BUILD_NUMBER}.conf"
    }
     
    stages {
        stage ('Execute Test cases') { 
            steps {
                withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'cortxadmin', usernameVariable: 'CORTX_USER_NAME', passwordVariable: 'CORTX_PASSWORD']]) {
                    script { build_stage = env.STAGE_NAME }
                    script {

                        dir('cortx-re') {
                            checkout changelog: false, poll: false, scm: [$class: 'GitSCM', branches: [[name: "${CORTX_RE_BRANCH}"]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: "${CORTX_RE_REPO}"]]]
                        }

                        sh label: 'run compatibility test', script: '''
                            #set +x
                            echo "Removing host entry"
                            RGW_SERVICE_IP=$(ping ${RGW_MASTER_NODE} -c 1|grep PING|cut -d "(" -f2|cut -d ")" -f1)
                            #sed -i '/s3test.seagate.com/d' /etc/hosts
                            echo "Adding host entry"
                            echo "$RGW_SERVICE_IP s3test.seagate.com" >> /etc/hosts

                            pushd cortx-re/scripts/automation/s3-test/
                                chmod +x ./*.sh
                                S3_MAIN_USER="s3-ceph-main_${BUILD_NUMBER}"
                                S3_EXT_USER="s3-ceph-ext_${BUILD_NUMBER}"
                                S3_TNT_USER="s3-ceph-tnt_${BUILD_NUMBER}"

                                # Set Main & Ext user cred in environment variable. This is required in config file

                                ./create_account.sh "${S3_MAIN_USER}" "${BUILD_NUMBER}" "${CORTX_USER_NAME}" "${CORTX_PASSWORD}"
                                S3_MAIN_USER_NAME="${S3_MAIN_USER}"
                                S3_MAIN_USER_ID="${S3_MAIN_USER}"
                                S3_MAIN_ACCESS_KEY=$(cat ${S3_MAIN_USER}_${BUILD_NUMBER}.log |tail -2|awk '{print $19}'|head -1|cut -d '"' -f2|sed -e "s/\r//g")
                                S3_MAIN_SECRET_KEY=$(cat ${S3_MAIN_USER}_${BUILD_NUMBER}.log |tail -2|awk '{print $21}'|head -1|cut -d '"' -f2|sed -e "s/\r//g")
                                
                                ./create_account.sh "${S3_EXT_USER}" "${BUILD_NUMBER}" "${CORTX_USER_NAME}" "${CORTX_PASSWORD}"
                                S3_ALT_USER_NAME="${S3_EXT_USER}"
                                S3_ALT_USER_ID="${S3_EXT_USER}"
                                S3_ALT_ACCESS_KEY=$(cat ${S3_ALT_USER_ID}_${BUILD_NUMBER}.log |tail -2|awk '{print $19}'|head -1|cut -d '"' -f2|sed -e "s/\r//g")
                                S3_ALT_SECRET_KEY=$(cat ${S3_ALT_USER_ID}_${BUILD_NUMBER}.log |tail -2|awk '{print $21}'|head -1|cut -d '"' -f2|sed -e "s/\r//g")

                                ./create_account.sh "${S3_TNT_USER}" "${BUILD_NUMBER}" "${CORTX_USER_NAME}" "${CORTX_PASSWORD}"
                                S3_TNT_USER_NAME="${S3_TNT_USER}"
                                S3_TNT_USER_ID="${S3_TNT_USER}"
                                S3_TNT_ACCESS_KEY=$(cat ${S3_TNT_USER_ID}_${BUILD_NUMBER}.log |tail -2|awk '{print $19}'|head -1|cut -d '"' -f2|sed -e "s/\r//g")
                                S3_TNT_SECRET_KEY=$(cat ${S3_TNT_USER_ID}_${BUILD_NUMBER}.log |tail -2|awk '{print $21}'|head -1|cut -d '"' -f2|sed -e "s/\r//g")

                                cp ./${INTEGRATION_TYPE}/${INTEGRATION_TYPE}.conf ${S3_TEST_CONF_FILE}

                                sed -i "s#<S3_MAIN_USER_NAME>#${S3_MAIN_USER_NAME}#;s#<S3_MAIN_USER_ID>#${S3_MAIN_USER_ID}#;s#<S3_MAIN_ACCESS_KEY>#${S3_MAIN_ACCESS_KEY}#;s#<S3_MAIN_SECRET_KEY>#${S3_MAIN_SECRET_KEY}#g;" ${S3_TEST_CONF_FILE}
                                sed -i "s#<S3_ALT_USER_NAME>#${S3_ALT_USER_NAME}#;s#<S3_ALT_USER_ID>#${S3_ALT_USER_ID}#;s#<S3_ALT_ACCESS_KEY>#${S3_ALT_ACCESS_KEY}#;s#<S3_ALT_SECRET_KEY>#${S3_ALT_SECRET_KEY}#g;" ${S3_TEST_CONF_FILE}
                                sed -i "s#<S3_TNT_USER_NAME>#${S3_TNT_USER_NAME}#;s#<S3_TNT_USER_ID>#${S3_TNT_USER_ID}#;s#<S3_TNT_ACCESS_KEY>#${S3_TNT_ACCESS_KEY}#;s#<S3_TNT_SECRET_KEY>#${S3_TNT_SECRET_KEY}#g;" ${S3_TEST_CONF_FILE}
                                sed -i "s/# port =.*/port = $RGW_PORT/g" ${S3_TEST_CONF_FILE}
                                sed -i "s/port =.*/port = $RGW_PORT/g" ${S3_TEST_CONF_FILE}

                                echo "---------------------------------"
                                echo ""
                                sh ./run_testcases.sh -c="${S3_TEST_CONF_FILE}" -i="${INTEGRATION_TYPE}" -tr="${S3_TEST_REPO}" -trr="${S3_TEST_REPO_REV}"
                                echo ""
                                echo "---------------------------------"
                                rm -rf ${S3_TEST_CONF_FILE} ${S3_TNT_USER_ID}_${BUILD_NUMBER}.log ${S3_ALT_USER_ID}_${BUILD_NUMBER}.log ${S3_MAIN_USER}_${BUILD_NUMBER}.log
                            popd
                        '''
                    }
                }
            }
        }        
    }
    post {
        always {
            script {
                archiveArtifacts artifacts: "cortx-re/scripts/automation/s3-test/*.txt, ${S3_TEST_CONF_FILE}, cortx-re/scripts/automation/s3-test/reports/*", onlyIfSuccessful: false, allowEmptyArchive: true
                junit testResults: 'cortx-re/scripts/automation/s3-test/reports/*.xml', testDataPublishers: [[$class: 'AttachmentPublisher']]  
                def mailRecipients = "shailesh.vaidya@seagate.com, kapil.jinna@seagate.com, shazia.ahmad@seagate.com, amit.kapil@seagate.com"
                emailext body: '''${SCRIPT, template="s3-comp-test-email-v2.template"}''',
                mimeType: 'text/html',
                recipientProviders: [requestor()], 
                subject: "[Jenkins] S3Ceph : ${currentBuild.currentResult}, ${JOB_BASE_NAME}#${BUILD_NUMBER}",
                to: "${mailRecipients}"
            }
            cleanWs()
        }
    }
}