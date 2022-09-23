import os

print("----------------- CLEANING DASHBOARD COMPONENTS -----------------")

print("\n\nPort Scanner Cleanup")

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


# CODACY CLEANUP

print("\n\nCodacy Cleanup")

# Deployment
os.system("kubectl delete deployments cortx-codacy")
print("")


# LOGSTASH CLEANUP

print("\n\nLogstash Cleanup")

# Deployment
os.system("kubectl delete deployments cortx-logstash")
print("")

# Configmap
os.system("kubectl delete configmap cortx-logstash")
print("")

# MongoDB Cleanup

print("\n\nMongoDB Cleanup")

# MongoDB Statefulset
os.system("kubectl delete statefulset cortx-port-scanner-mongodb")
print("")

# MongoDB Headless Service
os.system("kubectl delete services cortx-port-scanner-mongodb")
print("")


# CLEANUP CREDENTIAL SECRET

print("\nConfigs Cleanup")

# Credential Secret
os.system("kubectl delete secret dashboard-secret")
print("")

# Configmap
os.system("kubectl delete configmap dashboard-configmap")
print("")
