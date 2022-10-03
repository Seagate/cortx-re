import os

# MongoDB Statefulset
os.system("kubectl delete statefulset dashboard-mongodb -n dashboard")
print("")

# MongoDB Headless Service
os.system("kubectl delete services dashboard-mongodb -n dashboard")
print("")
