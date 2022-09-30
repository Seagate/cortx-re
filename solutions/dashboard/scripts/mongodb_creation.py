import os

print("----------------- MongoDB -----------------")

# Configmap
os.system("kubectl create -f ../config/mongodb/mongodb.yaml")
print("")
