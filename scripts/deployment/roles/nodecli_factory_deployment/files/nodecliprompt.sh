#!/usr/bin/expect

set timeout 120
spawn ssh -p [lindex $argv 2] -o StrictHostKeyChecking=no [lindex $argv 0]@[lindex $argv 1]

expect -re "nodecli>"
send "[lindex $argv 3]\r"
expect -re "nodecli>"
send "exit\r"
interact
