import os

print("----------------- CLEANING DASHBOARD COMPONENTS -----------------")

print("\n\nPort Scanner Cleanup")

# PortScanner Deployment
os.system("kubectl delete deployment dashboard-port-scanner -n dashboard")
print("")

# Service Account
os.system("kubectl delete serviceaccounts dashboard-port-scanner -n dashboard")
print("")

# Role
os.system("kubectl delete clusterrole dashboard-port-scanner")
print("")

# Role Binding
os.system("kubectl delete clusterrolebinding dashboard-port-scanner")
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

# CODACY CLEANUP

print("\n\nCodacy Cleanup")

# Deployment
os.system("kubectl delete deployments dashboard-codacy -n dashboard")
print("")


# LOGSTASH CLEANUP

print("\n\nLogstash Cleanup")

# Deployment
os.system("kubectl delete deployments dashboard-logstash -n dashboard")
print("")

# Configmap
os.system("kubectl delete configmap dashboard-logstash -n dashboard")
print("")

# MongoDB Cleanup

print("\n\nMongoDB Cleanup")

# MongoDB Statefulset
os.system("kubectl delete statefulset dashboard-mongodb -n dashboard")
print("")

# MongoDB Headless Service
os.system("kubectl delete services dashboard-mongodb -n dashboard")
print("")


# CLEANUP CREDENTIAL SECRET

print("\nConfigs Cleanup")

# Credential Secret
os.system("kubectl delete secret dashboard-secret -n dashboard")
print("")

# Configmap
os.system("kubectl delete configmap dashboard-configmap -n dashboard")
print("")
