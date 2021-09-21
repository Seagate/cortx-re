#!/bin/bash
USAGE="USAGE: bash $(basename "$0") [--config_dir <config_path>] [--data_dir <data_path>]
      [--help | -h]
Configure LDAP Directories.

where:
--config_dir        config path for openldap
--data_dir          data path for openldap
--help              display this help and exit
"

set -e

# Default config and data directories
CONFIG_PATH="/etc/cortx"
DATA_PATH="/var/data/cortx"

echo "Running entrypoint.sh script"

while test $# -gt 0
do
  case "$1" in
    --config_dir ) shift;
        CONFIG_PATH=$1
        ;;
    --data_dir ) shift;
        DATA_PATH=$1
        ;;
    --help | -h )
        echo "$USAGE"
        exit 1
        ;;
  esac
  shift
done
#echo "$CONFIG_PATH"
#echo "$DATA_PATH"

# Check if CONFIG_PATH exists else create
if [ ! -d "$CONFIG_PATH/openldap/" ]           
then
	mkdir -p $CONFIG_PATH/openldap
fi

# Check if DATA_PATH exists else create
if [ ! -d "$DATA_PATH/ldap/" ]
then
        mkdir -p $DATA_PATH/ldap
fi

# Checks if ldap configuration already on pvc
# If not present copy contents of /etc/openldap to CONFIG_PATH
if [ -z "$(ls -A $CONFIG_PATH/openldap/)" ]
then
	cp -r /etc/openldap/ $CONFIG_PATH
fi

# Change permissions of pvc directory
chown -R ldap.ldap $CONFIG_PATH/openldap/

# Start slapd
/usr/sbin/slapd -F $CONFIG_PATH/openldap/slapd.d -u ldap -h 'ldapi:/// ldap:///'

# This is to keep container running
sleep infinity
