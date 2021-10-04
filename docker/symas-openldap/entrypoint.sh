#!/bin/bash
USAGE="USAGE: bash $(basename "$0") [--rootdnpassword <rootdnpassword>][--config_dir <config_path>] [--data_dir <data_path>]
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
  python3 -c "import sys;sys.path.insert(1, '/usr/lib/python3.6/site-packages/cortx/utils/setup/openldap/');from base_configure_ldap import BaseConfig;BaseConfig.perform_base_config('$ROOTDN_PASSWORD',True,{'base_dn':'dc=seagate,dc=com' , 'bind_base_dn':'cn=admin,dc=seagate,dc=com', 'install_dir': '$CONFIG_PATH', 'data_dir': '$DATA_PATH'})" 
fi

kill -15 $(pidof slapd)

# Start slapd
/usr/sbin/slapd -F $CONFIG_PATH/openldap/slapd.d -u ldap -h 'ldapi:/// ldap:///' -d 0
