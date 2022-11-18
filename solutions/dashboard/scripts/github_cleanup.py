import os

print("----------------- Codacy -----------------")

os.system("kubectl delete cronjob dashboard-github -n dashboard")
print("")
