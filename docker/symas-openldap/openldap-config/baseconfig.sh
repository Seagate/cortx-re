#!/bin/bash -e
#
# Copyright (c) 2020 Seagate Technology LLC and/or its Affiliates
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# For any questions about this software or licensing,
# please email opensource@seagate.com or cortx-questions@seagate.com.
#


##################################
# configure OpenLDAP #
##################################

USAGE="USAGE: bash $(basename "$0") [--rootdnpasswd <passwd>] [--config_path <config_path>] [--data_path <data_path>] 
      [--forceclean] [--help | -h]
Install and configure OpenLDAP.

where:
--rootdnpasswd      optional rootdn password
--config_path       Configuration directory
--data_path         Data directory
--forceclean        Clean old openldap setup (** careful: deletes data **)
--help              display this help and exit
"

set -e

forceclean=false
ROOTDNPASSWORD=
CONFIG_PATH=
DATA_PATH=

echo "Running baseconfig.sh script"
if [ $# -lt 1 ]
then
  echo "$USAGE"
  exit 1
fi

while test $# -gt 0
do
  case "$1" in
    --rootdnpasswd ) shift;
        ROOTDNPASSWORD=$1
        ;;
    --config_path ) shift;
        CONFIG_PATH=$1
        ;;
    --data_path ) shift;
        DATA_PATH=$1
        ;;
    --forceclean )
        forceclean=true
        ;;
    --help | -h )
        echo "$USAGE"
        exit 1
        ;;
  esac
  shift
done

#Change data directory permission
chown -R ldap.ldap $DATA_PATH

INSTALLDIR="/opt/openldap-config"
# Clean up old configuration if any for idempotency
# Removing schemas
rm -f $CONFIG_PATH/openldap/slapd.d/cn\=config/cn\=schema/cn\=\{1\}s3user.ldif
rm -f $CONFIG_PATH/openldap/slapd.d/cn\=config/cn\=schema/*ppolicy.ldif
# Removing all loaded modules in ldap
rm -rf $CONFIG_PATH/openldap/slapd.d/cn\=config/cn\=module\{0\}.ldif
rm -rf $CONFIG_PATH/openldap/slapd.d/cn\=config/cn\=module\{1\}.ldif
rm -rf $CONFIG_PATH/openldap/slapd.d/cn\=config/cn\=module\{2\}.ldif
# Removing mdb configuration
rm -rf $CONFIG_PATH/openldap/slapd.d/cn\=config/olcDatabase\=\{2\}mdb
rm -rf $CONFIG_PATH/openldap/slapd.d/cn\=config/olcDatabase\=\{2\}mdb.ldif

# Data Cleanup
if [[ $forceclean == true ]]
then
  rm -rf $DATA_PATH/*
fi

#removes all hdb file instances if they exist, non-existence of files doesn't throw an error as well.
rm -f $CONFIG_PATH/openldap/slapd.d/cn\=config/olcDatabase=*hdb.ldif
cp -f $INSTALLDIR/olcDatabase\=\{2\}mdb.ldif $CONFIG_PATH/openldap/slapd.d/cn\=config/
sed -i "s/olcDbDirectory:.*/olcDbDirectory:\ ${DATA_PATH//\//\\/}/g" $CONFIG_PATH/openldap/slapd.d/cn\=config/olcDatabase={2}mdb.ldif
chgrp ldap /etc/openldap/certs/password # onlyif: grep -q ldap /etc/group && test -f /etc/openldap/certs/password


if [ -z "$ROOTDNPASSWORD" ]
then
        # Fetch password from User
        echo -en "\nPassword cant be empty "
fi

# generate encrypted password for rootDN
SHA=$(slappasswd -s $ROOTDNPASSWORD)
ESC_SHA=$(echo $SHA | sed 's/[/]/\\\//g')
EXPR='s/olcRootPW: *.*/olcRootPW: '$ESC_SHA'/g'

CFG_FILE=$(mktemp XXXX.ldif)
cp -f $INSTALLDIR/cfg_ldap.ldif $CFG_FILE
sed -i "$EXPR" $CFG_FILE

chkconfig slapd on

# start slapd
if [ ! -z $(pidof slapd) ]
then
   kill -15 $(pidof slapd)
fi
/usr/sbin/slapd -F $CONFIG_PATH/openldap/slapd.d -u ldap -h 'ldapi:/// ldap:///'

retry_count=1
while [[ $(ldapsearch -x -b 'cn=admin,cn=config' -h localhost 2>/dev/null | grep matchedDN:* | awk '{print $2}' | tr -d '\r') != "cn=config" ]]
do
    if [ $retry_count -eq 4 ]
    then
        exit 1
    fi
    echo "Retry: $retry_count"
    echo "Waiting for ldap service ..."
    sleep 1
    echo "Starting slapd process if not started yet"
    if [[ -z $(pidof slapd) ]]
    then
        /usr/sbin/slapd  -F $CONFIG_PATH/openldap/slapd.d -u ldap -h 'ldapi:/// ldap:///'
    fi
    retry_count=$((retry_count+1)) 
done
echo "started slapd"

# configure LDAP
ldapmodify -Y EXTERNAL -H ldapi:/// -w $ROOTDNPASSWORD -f $CFG_FILE
rm -f $CFG_FILE

# initialize ldap
ldapadd -x -D "cn=admin,dc=seagate,dc=com" -w "$ROOTDNPASSWORD" -f "$INSTALLDIR"/ldap-init.ldif -H ldapi:/// || /bin/true

# Enable IAM constraints
ldapadd -Y EXTERNAL -H ldapi:/// -w $ROOTDNPASSWORD -f $INSTALLDIR/iam-constraints.ldif

#Enable ppolicy schema
ldapmodify -D "cn=admin,cn=config" -w $ROOTDNPASSWORD -a -f $CONFIG_PATH/openldap/schema/ppolicy.ldif -H ldapi:///

# Enable password policy and configure
ldapmodify -D "cn=admin,cn=config" -w $ROOTDNPASSWORD -a -f $INSTALLDIR/ppolicymodule.ldif -H ldapi:///

ldapmodify -D "cn=admin,cn=config" -w $ROOTDNPASSWORD -a -f $INSTALLDIR/ppolicyoverlay.ldif -H ldapi:///

ldapmodify -x -a -H ldapi:/// -D cn=admin,dc=seagate,dc=com -w "$ROOTDNPASSWORD" -f "$INSTALLDIR"/ppolicy-default.ldif || /bin/true

# Enable slapd log with logLevel as "none"
# for more info : http://www.openldap.org/doc/admin24/slapdconfig.html
echo "Enable slapd log with logLevel"
ldapmodify -Y EXTERNAL -H ldapi:/// -w $ROOTDNPASSWORD -f $INSTALLDIR/slapdlog.ldif

# Set ldap search Result size
ldapmodify -Y EXTERNAL -H ldapi:/// -w $ROOTDNPASSWORD -f $INSTALLDIR/resultssizelimit.ldif
