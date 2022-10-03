import os

print("----------------- Credential Configs -----------------")

# Secret
os.system("kubectl delete secret dashboard-secret -n dashboard")
print("")

# Configmap
os.system("kubectl delete configmap dashboard-configmap -n dashboard")
print("")
