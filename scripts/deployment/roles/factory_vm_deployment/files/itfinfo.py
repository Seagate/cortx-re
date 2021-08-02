#!/usr/bin/python3
import netifaces as ni
import re

ipregex = "^((25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])\.){3}(25[0-5]|2[0-4][0-9]|1[0-9][0-9]|[1-9]?[0-9])$"
count = 0
skipitf = False
for iface in ni.interfaces():
    if iface == 'lo' or iface.startswith('vbox') or skipitf:
        skipitf = False
        continue
    try:
      ipaddr = ni.ifaddresses(iface)[ni.AF_INET][0]['addr']
      if ipaddr and re.search(ipregex, ipaddr) and count < 3:
        count += 1
        netmask = ni.ifaddresses(iface)[ni.AF_INET][0]['netmask']
        if count == 1:
          gateway = ni.gateways()['default'][ni.AF_INET][0]
          print(iface+","+ipaddr+","+netmask+","+gateway)
        elif count == 2:
          print(iface+","+ipaddr+","+netmask)
          skipitf = True
        else:
          print(iface+","+ipaddr+","+netmask)
    except:
      pass
if count < 3:
  print("FAILURE: No sufficient interfaces!! Minimum 3 interfaces with IP's are required.")
  exit(1)