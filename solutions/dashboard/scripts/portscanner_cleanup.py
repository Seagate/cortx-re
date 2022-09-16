import os

# PortScanner Deployment
os.system("kubectl delete deployment cortx-port-scanner")
print("")

# Service Account
os.system("kubectl delete serviceaccounts cortx-port-scanner")
print("")

# Role
os.system("kubectl delete roles cortx-port-scanner")
print("")

# Role Binding
os.system("kubectl delete rolebindings cortx-port-scanner")
print("")

# CR
os.system("kubectl delete cortxportscanners cortx-port-scanner")
print("")

# CRD
os.system("kubectl delete crd cortxportscanners.seagate.com")
print("")

# Configmap
os.system("kubectl delete configmaps cortx-port-scanner")
print("")