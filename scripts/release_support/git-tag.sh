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
			)
					
			//git config --global user.email "cortx-application@seagate.com"
			//git config --global user.name "$cortx-admin"
			wget -q $RELEASE_INFO_URL -O RELEASE.INFO
	
	for component in "${!COMPONENT_LIST[@]}"

	do
		dir=$(echo ${COMPONENT_LIST[$component]} |  awk -F'/' '{print $NF}')
		git clone --quiet ${COMPONENT_LIST[$component]} $dir > /dev/null

		rc=$?
		if [ $rc -ne 0 ]; then 
			echo "ERROR:git clone failed for $component"
		exit 1
		fi

		if [ $component == cortx-hare ] || [ $component == cortx-sspl ] || [ $component == cortx-ha ] || [ $component == cortx-fs ]; then
			COMMIT_HASH=$(grep $component RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | cut -d. -f1 |  sed 's/git//g');
		elif [ "$component" == "cortx-csm_agent" ] || [ "$component" == "cortx-csm_web" ]; then
			COMMIT_HASH=$(grep $component RELEASE.INFO | head -1 | awk -F['_'] '{print $3}' |  cut -d. -f1);
		else
			COMMIT_HASH=$(grep $component RELEASE.INFO | head -1 | awk -F['_'] '{print $2}' | sed 's/git//g');
		fi

		echo "Component: $component , Repo:  ${COMPONENT_LIST[$component]}, Commit Hash: ${COMMIT_HASH}"
		pushd $dir
			git tag -a "$GIT_TAG" "${COMMIT_HASH}" -m "Latest Release"
			git push origin "$GIT_TAG"
			echo "Component: $component , Tag: git tag -l $GIT_TAG is Tagged Successfully"
			git push origin --delete "$GIT_TAG"
		popd
	
	done
