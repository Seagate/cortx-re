import os

print("----------------- Codacy -----------------")

# Deployment
os.system("kubectl delete deployments dashboard-codacy -n dashboard")
print("")
