import os

print("----------------- Codacy -----------------")

# Deployment
os.system("kubectl delete deployments cortx-codacy")
print("")
