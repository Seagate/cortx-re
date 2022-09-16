## Elasticsearch and Kibana Installation

### Elastic Cloud on Kubernetes Installation:

Install custom resource definitions:

```
kubectl create -f https://download.elastic.co/downloads/eck/2.4.0/crds.yaml
```

Install the operator with its RBAC rules:

```
kubectl apply -f https://download.elastic.co/downloads/eck/2.4.0/operator.yaml
```

### Deploy an Elasticsearch Cluster:

**Apply Elasticsearch Specification**

- Do not delete the **version** attribute from the YAML
- Do not change the **volumeClaimTemplates** name
- Replace **storageClassName** to storage class like **local-path**

```
cat <<EOF | kubectl apply -f -
apiVersion: elasticsearch.k8s.elastic.co/v1
kind: Elasticsearch
metadata:
  name: cortx-elasticsearch
  namespace: cortx
spec:
  version: 8.4.1
  http:
    service:
      spec:
        type: NodePort
    tls:
      selfSignedCertificate:
        disabled: true
  nodeSets:
  - name: default
    count: 1
    config:
      node.store.allow_mmap: false
    volumeClaimTemplates:
    - metadata:
        name: elasticsearch-data
      spec:
        accessModes:
        - ReadWriteOnce
        resources:
          requests:
            storage: 5Gi
        storageClassName: <storageClassName>
EOF
```

**Monitor Cluster Health and Creation Process**

```
kubectl get elasticsearch
```

**Check Elasticsearch Pod Logs**

```
kubectl logs -f cortx-elasticsearch-es-default-0
```

**Get Elasticsearch Service**
Note the NodePort from the service

```
kubectl get service cortx-elasticsearch-es-http
```

**Get Nodes**
Note Primary node INTERNAL-IP

```
kubectl get nodes -o wide
```

**Open Web Browser**
Go to the following address

```
http://<master_node_internal_ip>:<node_port>
```

Enter username and password:

- Username: **elastic**
- Password:
  Run below command to get password

```
PASSWORD=$(kubectl get secret cortx-elasticsearch-es-elastic-user -o go-template='{{.data.elastic | base64decode}}')
echo $PASSWORD
```

### Deploy Kibana Instance

**Apply Kibana Specification**

- Do not delete the version attribute from the YAML

```
cat <<EOF | kubectl apply -f -
apiVersion: kibana.k8s.elastic.co/v1
kind: Kibana
metadata:
  name: cortx-kibana
  namespace: cortx
spec:
  version: 8.4.1
  count: 1
  elasticsearchRef:
    name: cortx-elasticsearch
  http:
    service:
      spec:
        type: NodePort
    tls:
      selfSignedCertificate:
        disabled: true
EOF
```

**Monitor Cluster Health and Creation Process**

```
kubectl get kibana
```

**Get Kibana Service**
Note the NodePort from the service

```
kubectl get service cortx-kibana-kb-http
```

**Get Nodes**
Note Primary node INTERNAL-IP

```
kubectl get nodes -o wide
```

**Open Web Browser**
Go to the following address

```
http://<master_node_internal_ip>:<node_port>
```

Enter username and password:

- Username: **elastic**
- Password:
  Run below command to get password

```
PASSWORD=$(kubectl get secret cortx-elasticsearch-es-elastic-user -o go-template='{{.data.elastic | base64decode}}')
echo $PASSWORD
```

You can click on **Explore on my own** after successful login.
