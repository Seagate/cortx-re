import os

# PortScanner Deployment
os.system("kubectl delete deployment dashboard-port-scanner -n dashboard")
print("")

# Service Account
os.system("kubectl delete serviceaccounts dashboard-port-scanner -n dashboard")
print("")

# Role
os.system("kubectl delete roles dashboard-port-scanner -n dashboard")
print("")

# Role Binding
os.system("kubectl delete rolebindings dashboard-port-scanner -n dashboard")
print("")

# CR
os.system("kubectl delete dashboardportscanners dashboard-port-scanner -n dashboard")
print("")

# CRD
os.system("kubectl delete crd dashboardportscanners.seagate.com")
print("")

# Configmap
os.system("kubectl delete configmaps dashboard-port-scanner -n dashboard")
print("")
