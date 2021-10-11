#!/bin/bash
USAGE="USAGE: bash $(basename "$0") [--rootdnpassword <rootdnpassword>] [--config_dir <config_path>] [--data_dir <data_path>]
      [--log_dir <log_path>] [--log_level <log_level>] [--help | -h]
Configure LDAP Directories.

where:
--config_dir        config path for openldap
--data_dir          data path for openldap
--help              display this help and exit
"

set -e

# Default config and data directories
CONFIG_PATH="/etc/3rd-party"
DATA_PATH="/var/data/3rd-party"
LOG_PATH="/var/log/3rd-party"
LOG_LEVEL="1"
ROOTDN_PASSWORD="seagate"

echo "Running entrypoint.sh script"

while test $# -gt 0
do
  case "$1" in
    --rootdnpassword ) shift;
        ROOTDN_PASSWORD=$1
        ;;
    --config_dir ) shift;
        CONFIG_PATH=$1
        ;;
    --data_dir ) shift;
        DATA_PATH=$1
        ;;
    --log_dir ) shift;
        LOG_PATH=$1
        ;;
    --log_level ) shift;
        LOG_LEVEL=$1
        ;;
    --help | -h )
        echo "$USAGE"
        exit 1
        ;;
  esac
  shift
done

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

# Check if LOG_PATH exists else create
if [ ! -d "$LOG_PATH" ]
then
        mkdir -p $LOG_PATH
fi

perform_base_config=false

# Checks if ldap configuration already on pvc
# If not present copy contents of /etc/openldap to CONFIG_PATH
if [ -z "$(ls -A $CONFIG_PATH/openldap/)" ]
then
	cp -r /etc/openldap/ $CONFIG_PATH
        perform_base_config=true
fi

# Change permissions of pvc directory
chown -R ldap.ldap $CONFIG_PATH/openldap/

# Start slapd
/usr/sbin/slapd -F $CONFIG_PATH/openldap/slapd.d -u ldap -h 'ldapi:/// ldap:///'

# Check if configuration already present
# Perform base config
if [ "$perform_base_config" == true ]
then
  DATA_PATH=${DATA_PATH}/ldap
  sh /opt/openldap-config/baseconfig.sh --rootdnpasswd $ROOTDN_PASSWORD --config_path $CONFIG_PATH --data_path $DATA_PATH
fi

kill -15 $(pidof slapd)

# Start slapd
/usr/sbin/slapd -F $CONFIG_PATH/openldap/slapd.d -u ldap -h 'ldapi:/// ldap:///' -d $LOG_LEVEL &> $LOG_PATH/openldap.log
