import os

print("----------------- Logstash -----------------")

# Deployment
os.system("kubectl delete deployments cortx-logstash")
print("")

# Configmap
os.system("kubectl delete configmap cortx-logstash")
print("")