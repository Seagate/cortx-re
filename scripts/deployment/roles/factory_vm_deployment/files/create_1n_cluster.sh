#!/bin/expect -f
# ./create_cluster.sh node1 node2 node3 pass build_url
spawn cortx_setup cluster create [lindex $argv 0] --name cortx_cluster --site_count 1 --storageset_count 1 --target_build [lindex $argv 1] 
set timeout 1200
expect {
    timeout {
        puts "Connection timed out"
        exit 1
    }

    "assword:" {
        send -- "[lindex $argv 2]\r"
        exp_continue
    }
}