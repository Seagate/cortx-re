import os

# MongoDB Statefulset
os.system("kubectl delete statefulset cortx-port-scanner-mongodb")
print("")

# MongoDB Headless Service
os.system("kubectl delete services cortx-port-scanner-mongodb")
print("")