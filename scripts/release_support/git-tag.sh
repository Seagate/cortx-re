#!/bin/bash

declare -A COMPONENT_LIST=(
                        [cortx-s3server]="https://$PASSWD@github.com/Seagate/cortx-s3server.git"
                        [cortx-motr]="https://$PASSWD@github.com/Seagate/cortx-motr.git"
                        [cortx-hare]="https://$PASSWD@github.com/Seagate/cortx-hare.git"
                        [cortx-ha]="https://$PASSWD@github.com/Seagate/cortx-ha.git"
                        [cortx-prvsnr]="https://$PASSWD@github.com/Seagate/cortx-prvsnr.git"
                        [cortx-sspl]="https://$PASSWD@github.com/Seagate/cortx-monitor.git"
                        [cortx-csm_agent]="https://$PASSWD@github.com/Seagate/cortx-manager.git"
                        [cortx-csm_web]="https://$PASSWD@github.com/Seagate/cortx-management-portal.git"
                        [cortx-py-utils]="https://$PASSWD@github.com/Seagate/cortx-utils.git"
                        [cortx-prereq]="https://$PASSWD@github.com/Seagate/cortx-re.git"
                )
				
		REPO_LIST=(
                        [cortx-s3server]="https://$PASSWD@github.com/Seagate/cortx-s3server/releases"
                        [cortx-motr]="https://$PASSWD@github.com/Seagate/cortx-motr/releases"
                        [cortx-hare]="https://$PASSWD@github.com/Seagate/cortx-hare/releases"
                        [cortx-ha]="https://$PASSWD@github.com/Seagate/cortx-ha/releases"
                        [cortx-prvsnr]="https://$PASSWD@github.com/Seagate/cortx-prvsnr/releases"
                        [cortx-sspl]="https://$PASSWD@github.com/Seagate/cortx-monitor/releases"
                        [cortx-csm_agent]="https://$PASSWD@github.com/Seagate/cortx-manager/releases"
                        [cortx-csm_web]="https://$PASSWD@github.com/Seagate/cortx-management-portal/releases"
                        [cortx-py-utils]="https://$PASSWD@github.com/Seagate/cortx-utils/releases"
                        [cortx-prereq]="https://$PASSWD@github.com/Seagate/cortx-re/releases"
                )	

                        git config --global user.email "cortx-applications@seagate.com"
                        git config --global user.name "cortx admin"
                        wget -q "$RELEASE_INFO_URL" -O RELEASE.INFO

        for component in "${!COMPONENT_LIST[@]}"

        do
                dir="$(echo "${COMPONENT_LIST[$component]}" |  awk -F'/' '{print $NF}')"
                git clone --quiet "${COMPONENT_LIST["$component"]}" "$dir" > /dev/null

                rc=$?
                if [ "$rc" -ne 0 ]; then
                        echo "ERROR:git clone failed for "$component""
                exit 1
                fi

                if [ "$component" == cortx-hare ] || [ "$component" == cortx-sspl ] || [ "$component" == cortx-ha ] || [ "$component" == cortx-fs ] || [ "$component" == cortx-py-utils ] || [ "$component" == cortx-prereq ]; then
                        COMMIT_HASH="$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | cut -d. -f1 |  sed 's/git//g')";
                elif [ "$component" == "cortx-csm_agent" ] || [ "$component" == "cortx-csm_web" ]; then
                        COMMIT_HASH="$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $3}' |  cut -d. -f1)";
                else
                        COMMIT_HASH="$(grep "$component" RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | sed 's/git//g')";
                fi

                echo "Component: "$component" , Repo:  "${COMPONENT_LIST[$component]}", Commit Hash: "${COMMIT_HASH}""
                pushd "$dir"
                if [ "$GIT_TAG" != "" ]; then
			git tag -a "$GIT_TAG" "${COMMIT_HASH}" -m "$TAG_MESSAGE";
                        git push origin "$GIT_TAG";
                        echo "Component: $component , Tag: git tag -l $GIT_TAG is Tagged Successfully";
                        git tag -l "$GIT_TAG";
                else
                        echo "Tag is not successful. Please pass value to GIT_TAG";
                fi
				
		#if [ "$REL_TAG" != "" ]; then
		#	echo "Release will be set for all the components";
		if [ "$REL_TAG" != "" ] || [ "$component" == cortx-hare ] || [ "$component" == cortx-sspl ] || [ "$component" == cortx-ha ] || [ "$component" == cortx-fs ] || [ "$component" == cortx-py-utils ] || [ "$component" == cortx-prereq ] || [ "$component" == "cortx-csm_agent" ] || [ "$component" == "cortx-csm_web" ]; then
			echo "Component: "$component" , Repo:  "${REPO_LIST[$component]}";
			curl -H "Accept: application/vnd.github.v3+json"  "${REPO_LIST[$component]}" -d '{"tag_name":"Cred-Test4", "name":"Release4"}';                        
                else
                        echo "Release is not successful. Please pass value to REL_TAG";
		fi
				
                if [ "$DEBUG" = true ]; then
			git push origin --delete "$GIT_TAG";
                else
			echo "Run in Debug mode if current Git tag needs to be deleted";
                fi
				
	popd
done
