#!/usr/bin/env groovy
// Please configure branch and os_version as string parameters in Jenkins configuration manually.
pipeline {
          
    agent {
        node {
            label "docker-${os_version}-node"
        }
    }
    
    environment {
        version = "2.0.0"
        third_party_version = "2.0.0-k8"
        release_dir = "/mnt/bigstorage/releases/cortx"
        integration_dir = "$release_dir/github/$branch/$os_version"
        components_dir = "$release_dir/components/github/$branch/$os_version"
        release_tag = "$BUILD_ID"
        BUILD_TO_DELETE = ""
        passphrase = credentials('rpm-sign-passphrase')
        ARTIFACT_LOCATION = "http://cortx-storage.colo.seagate.com/releases/cortx/github/$branch/$os_version"
        third_party_dir = "$release_dir/third-party-deps/rockylinux/$os_version-$third_party_version/"
        python_deps = "$release_dir/third-party-deps/python-deps/python-packages-2.0.0-latest"
        // WARNING : 'rm' command where used in this dir path, be conscious while changing the value  
        cortx_build_dir = "$release_dir/github/$branch/$os_version/cortx_builds" 
    }
    
    options {
        timeout(time: 120, unit: 'MINUTES')
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()  
    }
        
    stages {    
    
        stage('Install Dependecies') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Installed Dependecies', script: '''
                    yum install -y expect rpm-sign rng-tools genisoimage python3-pip
                    #systemctl start rngd
                '''    
            }
        }

        stage('Checkout Release scripts') {
            steps {
                script { build_stage = env.STAGE_NAME }
                checkout([$class: 'GitSCM', branches: [[name: "main"]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'AuthorInChangelog']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'cortx-admin-github', url: 'https://github.com/Seagate/cortx-re']]])
            }
        }
            
        stage ('Collect Component RPMS') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Copy RPMS', script:'''
                    for env in "dev";
                    do
                        mkdir -p $integration_dir/$release_tag/$env
                        #Temporary change for RGW feature branch
                        #pushd /mnt/bigstorage/releases/cortx/components/github/rgw/rockylinux-8.4/dev
                        #RGW_BRANCH_COMPONENTS="provisioner hare cortx-utils csm-agent"
                        MAIN_BRANCH_COMPONENTS="motr cortx-rgw cortx-rgw-integration cortx-ha provisioner hare cortx-utils csm-agent"
                        #for component in $RGW_BRANCH_COMPONENTS
                        #do
                        #    echo -e "Copying RPM's for $component"
                        #    if ls $component/last_successful/*.rpm 1> /dev/null 2>&1; then
                        #    cp $component/last_successful/*.rpm $integration_dir/$release_tag/$env
                        #    fi
                        #done

                        pushd $components_dir/$env
                        for component in  $MAIN_BRANCH_COMPONENTS
                        do
                            echo -e "Copying RPM's for $component"
                            if ls $component/last_successful/*.rpm 1> /dev/null 2>&1; then
                                cp $component/last_successful/*.rpm $integration_dir/$release_tag/$env
                            fi
                        done
                        popd
                    done

                    mkdir -p $integration_dir/$release_tag/prod
                    cp -n -r $integration_dir/$release_tag/dev/* $integration_dir/$release_tag/prod/

                    pushd $integration_dir/$release_tag/prod
                        rm -rf cortx-[lp]*-debuginfo-*.rpm
                    popd
                '''
            }
        }

        stage('RPM Validation') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Validate RPMS for Motr Dependency', script:'''
                for env in "dev" "prod";
                do
                    set +x
                    echo "VALIDATING $env RPM'S................"
                    echo "-------------------------------------"
                    pushd $integration_dir/$release_tag/$env
                    motr_rpm=$(ls -1 | grep "cortx-motr-$version")
                    motr_rpm_release=`rpm -qp ${motr_rpm} --qf '%{RELEASE}' | tr -d '\040\011\012\015'`
                    motr_rpm_version=`rpm -qp ${motr_rpm} --qf '%{VERSION}' | tr -d '\040\011\012\015'`
                    motr_rpm_release_version="${motr_rpm_version}-${motr_rpm_release}"
                    for component in `ls -1`
                    do
                        motr_dep=`echo $(rpm -qpR ${component} | grep -E "cortx-motr =") | cut -d= -f2 | tr -d '\040\011\012\015'`
                        if [ -z "$motr_dep" ]
                        then
                            echo "\033[1;33m $component has no dependency to Motr - Validation Success \033[0m "
                        else
                            if [ "$motr_dep" = "$motr_rpm_release_version" ]; then
                                echo "\033[1;32m $component Motr version matches with integration Motr rpm ($motr_rpm_release_version) Good to Go - Validation Success \033[0m "
                            else
                                echo "\033[1;31m $component Motr version ( $motr_dep ) mismatchs with integration Motr rpm ( $motr_rpm_release_version ) - Validation Failed \033[0m"        
                                exit 1
                            fi
                        fi
                    done
                done
                '''
            }
        }
        
        stage ('Sign rpm') {
            steps {
                script { build_stage = env.STAGE_NAME }
                                
                sh label: 'Generate Key', script: '''
                    set +x
                    pushd scripts/rpm-signing
                    expiredKey=$(gpg --list-keys | grep -B1 "Seagate"|head -1|tr -d " ")
                    echo "$expiredKey"
                    gpg --batch --yes --quiet --delete-secret-key $expiredKey >/dev/null 2>&1
                    if [ $? != 0 ]; then
                        echo "Remove old gpg secret failed"
                        exit 1
                    fi
                    gpg --batch --quiet --yes --delete-keys $expiredKey >/dev/null 2>&1
                    if [ $? != 0 ]; then
                        echo "Remove old gpg key failed"
                        exit 1
                    fi
                    sed 's/--passphrase-fd 3 //g' gpgoptions >> ~/.rpmmacros
                    sed -i -e "s/Passphrase:.*/Passphrase: ${passphrase}/g" genkey-batch
                    gpg --batch --gen-key genkey-batch
                    gpg --export -a 'Seagate'  > RPM-GPG-KEY-Seagate
                    rpm --import RPM-GPG-KEY-Seagate
                    popd
                '''

                sh label: 'Sign RPM', script: '''
                    set +x
                    count="0"
                    for env in "dev" "prod";
                    do
                        pushd scripts/rpm-signing
                            chmod +x rpm-sign.sh
                            cp RPM-GPG-KEY-Seagate $integration_dir/$release_tag/$env/
                            for rpm in `ls -1 $integration_dir/$release_tag/$env/*.rpm`
                            do
                                if [ $count == "0" ]; then
                                    ./rpm-sign.sh ${passphrase} $rpm
                                    count="1"
                                else
                                    rpm --addsign $rpm
                                fi
                            done
                        popd
                    done    
                '''
            }
        }                
        stage ('Repo Creation') {
            steps {
                script { build_stage = env.STAGE_NAME }
        
                sh label: 'Repo Creation', script: '''

                    for env in "dev" "prod";
                    do
                        pushd $integration_dir/$release_tag/$env/
                            rpm -qi createrepo || yum install -y createrepo
                            createrepo .
                        popd
                    done
                    
                '''
            }
        }    

        stage('Release cortx_build') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Release cortx_build', script: '''
                    mkdir -p $cortx_build_dir
                    pushd $cortx_build_dir
                        test -d $release_tag && rm -f $release_tag
                        mkdir $release_tag && pushd $release_tag
                            ln -s $(readlink -f $third_party_dir) 3rd_party
                            ln -s $(readlink -f $python_deps) python_deps
                        popd
                    popd
                '''
            }
        }
        
        stage ('Build Release Info') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Build Release Info', script: """
                    pushd scripts/release_support
                        sh build_release_info.sh -b $branch -v $version -l $integration_dir/$release_tag/dev -t $cortx_build_dir/$release_tag/3rd_party
                        sh build_release_info.sh -b $branch -v $version -l $integration_dir/$release_tag/prod -t $cortx_build_dir/$release_tag/3rd_party
                        sh build_readme.sh $integration_dir/$release_tag
                    popd
                    
                    cp $integration_dir/$release_tag/README.txt .
                    cp $integration_dir/$release_tag/dev/RELEASE.INFO .
                    
                """
                sh label: 'Generate Changelog', script: """
                    pushd scripts/release_support
                        sh +x changelog.sh ${currentBuild.previousBuild.getNumber()} ${currentBuild.number} ${ARTIFACT_LOCATION}
                    popd
                    cp /root/git_build_checkin_stats/clone/git-build-checkin-report.txt CHANGESET.txt 
                    cp CHANGESET.txt $integration_dir/$release_tag/dev
                    cp CHANGESET.txt $integration_dir/$release_tag/prod
                """
            }
        }
        
        stage ('Additional Files') {
            steps {

                sh label: 'Additional Files', script:'''
                mkdir -p $cortx_build_dir/$release_tag/cortx_iso
                mv $integration_dir/$release_tag/prod/* $cortx_build_dir/$release_tag/cortx_iso
                mkdir -p $integration_dir/$release_tag/prod/iso
                mv $cortx_build_dir/$release_tag/* $integration_dir/$release_tag/prod
                cp $integration_dir/$release_tag/prod/*/*.INFO $integration_dir/$release_tag/prod
                        
                rm -rf "$cortx_build_dir/$release_tag"
                '''
            }
        }

       stage ('Tag last_successful') {
            steps {
                script { build_stage = env.STAGE_NAME }
                sh label: 'Tag last_successful', script: '''
                    pushd $integration_dir
                    test -L last_successful && rm -f last_successful
                    test -L last_successful_prod && rm -f last_successful_prod
                    ln -s $integration_dir/$release_tag/dev last_successful
                    ln -s $integration_dir/$release_tag/prod last_successful_prod
                    
                    #symlinks for RGW branch builds
                    test -L last_successful && rm -f /mnt/bigstorage/releases/cortx/github/rgw/rockylinux-8.4/last_successful
                    test -L last_successful_prod && rm -f /mnt/bigstorage/releases/cortx/github/rgw/rockylinux-8.4/last_successful_prod
                    ln -s $integration_dir/$release_tag/dev /mnt/bigstorage/releases/cortx/github/rgw/rockylinux-8.4/last_successful
                    ln -s $integration_dir/$release_tag/prod /mnt/bigstorage/releases/cortx/github/rgw/rockylinux-8.4/last_successful_prod
                    popd
                '''
            }
        }

        stage ("Build CORTX images") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    try {
                        def build_cortx_images = build job: 'cortx-docker-images', wait: true,
                                    parameters: [
                                        string(name: 'CORTX_RE_URL', value: "https://github.com/Seagate/cortx-re"),
                                        string(name: 'CORTX_RE_BRANCH', value: "main"),
                                        string(name: 'BUILD', value: "${ARTIFACT_LOCATION}/${release_tag}/prod"),
                                        string(name: 'GITHUB_PUSH', value: "yes"),
                                        string(name: 'TAG_LATEST', value: "yes"),
                                        string(name: 'DOCKER_REGISTRY', value: "cortx-docker.colo.seagate.com"),
                                        string(name: 'EMAIL_RECIPIENTS', value: "DEBUG"),
                                        string(name: 'OS', value: "${os_version}"),
                                        string(name: 'CORTX_IMAGE', value: "all")
                                        ]
                    env.cortx_all_image = build_cortx_images.buildVariables.cortx_all_image
                    env.cortx_rgw_image = build_cortx_images.buildVariables.cortx_rgw_image
                    env.cortx_data_image = build_cortx_images.buildVariables.cortx_data_image
                    env.cortx_control_image = build_cortx_images.buildVariables.cortx_control_image
                    } catch (err) {
                        build_stage = env.STAGE_NAME
                        error "Failed to Build CORTX images"
                    }
                }
            }
       } 

        stage ("Deploy") {
            steps {
                script { build_stage = env.STAGE_NAME }
                script {
                    build job: "K8s-1N-deployment", propagate: false, wait: false,
                    parameters: [
                        string(name: 'CORTX_RE_BRANCH', value: "main"),
                        string(name: 'CORTX_RE_REPO', value: "https://github.com/Seagate/cortx-re"),
                        string(name: 'CORTX_SERVER_IMAGE', value: "${env.cortx_rgw_image}"),
                        string(name: 'CORTX_DATA_IMAGE', value: "${env.cortx_data_image}"),
                        string(name: 'CORTX_CONTROL_IMAGE', value: "${env.cortx_control_image}")
                    ]
                    build job: "K8s-3N-deployment", propagate: false, wait: false,
                    parameters: [
                        string(name: 'CORTX_RE_BRANCH', value: "main"),
                        string(name: 'CORTX_RE_REPO', value: "https://github.com/Seagate/cortx-re"),
                        string(name: 'CORTX_SERVER_IMAGE', value: "${env.cortx_rgw_image}"),
                        string(name: 'CORTX_DATA_IMAGE', value: "${env.cortx_data_image}"),
                        string(name: 'CORTX_CONTROL_IMAGE', value: "${env.cortx_control_image}")
                    ]
                }
            }
        }
    }
    
    post {
    
        always {
            script {
                    
                currentBuild.upstreamBuilds?.each { b -> env.upstream_project = "${b.getProjectName()}";env.upstream_build = "${b.getId()}" }
                env.release_build_location = "http://cortx-storage.colo.seagate.com/releases/cortx/github/${branch}/${os_version}/${env.release_tag}"
                env.release_build = "${env.release_tag}"
                env.build_stage = "${build_stage}"

                def toEmail = "shailesh.vaidya@seagate.com"
                
                emailext ( 
                        body: '''${SCRIPT, template="K8s-release-email.template"}''',
                        mimeType: 'text/html',
                        subject: "[Jenkins Build ${currentBuild.currentResult}] : ${env.JOB_NAME}",
                        attachLog: true,
                        to: toEmail,
                        attachmentsPattern: 'CHANGESET.txt'
                    )

                archiveArtifacts artifacts: "README.txt, RELEASE.INFO, CHANGESET.txt", onlyIfSuccessful: false, allowEmptyArchive: true
            }
        }
    }
}