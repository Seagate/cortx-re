#!/bin/expect -f

set timeout 1200
spawn ssh -p [lindex $argv 2] -o StrictHostKeyChecking=no [lindex $argv 0]@[lindex $argv 1]

expect {
    timeout {
        puts "Connection timed out"
        exit 1
    }

    -re "nodecli>" {
         send "[lindex $argv 3]\r"
         exp_continue
    }

    -re "Enter nodeadmin user password for.*" {
         send -- "[lindex $argv 2]\r"
         exp_continue
    }
    -re "Bootstrap Done.*" {
         send -- "exit\r"
         exp_continue
    }
}
