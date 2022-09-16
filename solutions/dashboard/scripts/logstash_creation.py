import os

print("----------------- Logstash -----------------")

# Configmap
os.system("kubectl create -f ../config/logstash/LogstashConfigmap.yaml")
print("")

# Deployment
os.system("kubectl create -f ../config/logstash/LogstashDeployment.yaml")
print("")