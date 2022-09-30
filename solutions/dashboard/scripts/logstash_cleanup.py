import os

print("----------------- Logstash -----------------")

# Deployment
os.system("kubectl delete deployments dashboard-logstash -n dashboard")
print("")

# Configmap
os.system("kubectl delete configmap dashboard-logstash -n dashboard")
print("")
