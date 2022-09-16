import os

print("----------------- PORT SCANNER -----------------")

os.system("kubectl create -f ../config/portscanner/PortScannerCRD.yaml")
print("")

os.system("kubectl create -f ../config/portscanner/PortScannerCR.yaml")
print("")

os.system("kubectl create -f ../config/portscanner/PortScannerAccount.yaml")
print("")

os.system("kubectl create -f ../config/portscanner/PortScannerDeployment.yaml")
print("")
