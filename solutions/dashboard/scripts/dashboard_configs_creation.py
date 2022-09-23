import os

print("----------------- Dashboard Configs -----------------")

# Secret Creation
os.system("kubectl create -f ../config/DashboardSecret.yaml")
print("")

# Configmap Creation
os.system("kubectl create -f ../config/DashboardConfigmap.yaml")
print("")
