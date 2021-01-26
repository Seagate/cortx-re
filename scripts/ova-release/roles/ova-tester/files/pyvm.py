from pyVim import connect
from config import *
from pyVmomi import vim, vmodl
import ssl
import os
import requests
import urllib3
import re
import time
import sys

vsphere_host = sys.argv[1]
vsphere_user = sys.argv[2]
vsphere_pass = sys.argv[3]
vm_uuid = sys.argv[4]
vm_user = sys.argv[5]
vm_pass = sys.argv[6]

urllib3.disable_warnings()
service_instance = connect.SmartConnectNoSSL(host=str(vsphere_host),user=str(vsphere_user) , pwd=str(vsphere_pass))

content = service_instance.RetrieveContent()

#find a VM
vm = content.searchIndex.FindByUuid(datacenter=None, uuid=str(vm_uuid), vmSearch=True, instanceUuid=False)

creds = vim.vm.guest.NamePasswordAuthentication(username=str(vm_user), password=str(vm_pass))

pm = content.guestOperationsManager.processManager


#executes and saves sample.txt into server
ps = vim.vm.guest.ProcessManager.ProgramSpec(programPath='/sbin/ifconfig', arguments="ens32 | grep 'inet' | cut -d: -f2 | awk '{print $2}' > ip.txt")
res = pm.StartProgramInGuest(vm, creds, ps)
#if res > 0:
#    print "Program submitted, PID is %d" % res
#    pid_exitcode = pm.ListProcessesInGuest(vm, creds,[res]).pop().exitCode
    # If its not a numeric result code, it says None on submit
#    while (re.match('[^0-9]+', str(pid_exitcode))):
#       print "Program running, PID is %d" % res
#       time.sleep(5)
#       pid_exitcode = pm.ListProcessesInGuest(vm, creds,[res]).pop().exitCode
#       if (pid_exitcode == 0):
#           print "Program %d completed with success" % res
#           break
       # Look for non-zero code to fail
#       elif (re.match('[1-9]+', str(pid_exitcode))):
#           print "ERROR: Program %d completed with Failute" % res
#           print "ERROR: More info on process"
#           print pm.ListProcessesInGuest(vm, creds, [res])
#           break

#local file destination
dest="/{}/ip.txt".format(str(vm_user))
#remote server file destination
src="/{}/ip.txt".format(str(vm_user))
fti = content.guestOperationsManager.fileManager.InitiateFileTransferFromGuest(vm, creds, src)

resp=requests.get(fti.url, verify=False)
print(str(resp.content).strip())
