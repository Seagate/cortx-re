## Dashboard Deployment using Helm Chart

Clone cortx-re repository and change directory to /root/repositories/cortx-re/solutions/dashboard/charts

```
git clone https://github.com/Seagate/cortx-re && cd $PWD/cortx-re/solutions/dashboard/charts
```

### Storage Class:
Make sure the reclainPolicy should be Retain
```
kubectl create -f ./prerequisites/local_path_provisioner.yaml
```

### Namespace:

Create the namespaces
```
kubectl create -f ./prerequisites/namespaces.yaml
```

### Secrets:

- #### elastic-user: 
  Replace the <elasticsearch_password> with your password in base64 format.
- #### dashboard-secret: 
  Fill the credentials in base64 format. If you don't need any tool you can skip that tool credentials.

```
kubectl create -f ./prerequisites/elastic-user.yaml
kubectl create -f ./prerequisites/dashboard-secret.yaml
```

### Elasticsearch Helm Chart

The **sample-value-yamls/sample-elastic-project-values.yaml** is provided for modification in origional **values.yaml**. You can also verify the origional values.yaml in **./elastic-project** directory

Installtion without custom values.yaml file:
```
helm install elastic-project ./elastic-project/
```

Installtion with custom values.yaml file:
```
helm install elastic-project ./elastic-project/ --values ./sample-value-yamls/sample-elastic-project-values.yaml
```

### Dashboard Helm Chart
The **sample-value-yamls/sample-dashboard-project-values.yaml** is provided for modification in origional **values.yaml**. You can also verify the origional values.yaml in **./dashboard-project** directory

Installtion without custom values.yaml file:
```
helm install dashboard-project ./dashboard-project/
```

Installtion with custom values.yaml file:
```
helm install dashboard-project ./dashboard-project/ --values ./sample-value-yamls/sample-dashboard-project-values.yaml
```