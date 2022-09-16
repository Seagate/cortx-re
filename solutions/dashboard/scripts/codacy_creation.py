import os

print("----------------- Codacy -----------------")

os.system("kubectl create -f ../config/codacy/CodacyDeployment.yaml")
print("")