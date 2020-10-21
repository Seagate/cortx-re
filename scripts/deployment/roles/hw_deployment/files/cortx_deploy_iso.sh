#!/bin/expect -f
# ./cortx_deploy_iso.sh node1 node2 pass build

set SRV_NODE1 [lindex $argv 0];
set SRV_NODE2 [lindex $argv 1];
set PASSWORD [lindex $argv 2];
set BUILD [lindex $argv 3];

spawn provisioner auto_deploy \
    --console-formatter full \
    --logfile \
    --logfile-filename /var/log/seagate/provisioner/setup.log \
    --config-path /root/config.ini \
    --ha \
    --source iso \
    --iso-cortx $BUILD \
    srvnode-1:$SRV_NODE1 \
    srvnode-2:$SRV_NODE2

set timeout 5400

expect {

    timeout {
        puts "Connection timed out"
        exit 1
    }

    "assword:" {
        send -- "$PASSWORD\r"
        exp_continue
    }
}
