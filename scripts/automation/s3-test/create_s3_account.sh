#!/bin/expect -f
# ./create_s3_account.sh username email ldap_user ldap_pwd

set USER_NAME [lindex $argv 0];
set USER_EMAIL [lindex $argv 1];
set LDAP_USER [lindex $argv 2];
set LDAP_PWD [lindex $argv 3];

spawn s3iamcli CreateAccount -n $USER_NAME -e $USER_EMAIL
set timeout 20
expect {
    timeout {
        puts "Connection timed out"
        exit 1
    }

    "Ldap User Id:" {
        send "$LDAP_USER\r"
        exp_continue
    }

    "Ldap password:" {
        send -- "$LDAP_PWD\r"
        exp_continue
    }
}