import os

print("----------------- Codacy -----------------")

os.system("kubectl delete cronjob dashboard-codacy -n dashboard")
print("")
