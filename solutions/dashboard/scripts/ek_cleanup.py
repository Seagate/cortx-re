import os

print("----------------- Elasticsearch and Kibana Cleanup -----------------")

# Deployment
os.system("kubectl get namespaces --no-headers -o custom-columns=:metadata.name \
  | xargs -n1 kubectl delete elastic --all -n")
print("")