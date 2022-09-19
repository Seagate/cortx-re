import os

print("----------------- Credential Configs -----------------")

# Secret
os.system("kubectl delete secret dashboard-secret")
print("")

# Configmap
os.system("kubectl delete configmap dashboard-configmap")
print("")
