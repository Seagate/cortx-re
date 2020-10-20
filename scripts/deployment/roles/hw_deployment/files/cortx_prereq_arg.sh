#! /bin/bash
SUB_LIST=$(subscription-manager list | grep Status: | awk '{ print $2 }' && subscription-manager status | grep 'Overall Status:' | awk '{ print $3 }')
SUB_REPO=$(subscription-manager repos --list | grep rhel-ha-for-rhel-7-server-rpms)

OS_FAMILY=$(cat /etc/redhat-release)

if [[ "${OS_FAMILY}" == *"Red Hat"* ]] && [[ "${SUB_LIST}" == *"Subscribed"* ]] && [[ "${SUB_LIST}" == *"Current"* ]] && [[ "${SUB_REPO}" == *" rhel-ha-for-rhel-7-server-rpms"* ]];then	
	echo ""
elif [[ "${OS_FAMILY}" != *"Red Hat"* ]]; then
	echo ""
else
	echo "--disable-sub-mgr"
fi