# Dashboard Application deployment

The following sections discusses setps to set up the K8s cluster and deploy the Dashbaord Application. Plese setup 3 Node Kubernetes cluster with below specifications. The minimum prerequisites are enlisted to make sure the cluster set up process is smooth

## Prerequisites

Following are the minimum system specifications required to set up the K8s cluster and Dashbaord Application. 

-  RAM: 16GB
-  CPU: 8Core
-  DISK: 9 Disks (1 with 50GB (For operating system) and rest 8 with 25GB per disk)
-  OS: CentOS 7.9 (64-bit)

## Kubernetes Cluster Setup

-  Please follow steps from [Kubernetes Installation](../../community-deploy/CORTX-Deployment.md#install-k8s-cluster) to setup Kubernetes cluster. 

## Deploy Dashboard Application on Kubernetes Cluster using kubectl 

### Execute following commands to deploy Dashbaord application on Kubernetes cluster using kubectl CLI

- Clone cortx-re repository and change directory to cortx-re/solutions/kubernetes
```
git clone https://github.com/Seagate/cortx-re/ -b dashboard && cd ./cortx-re/solutions/dashboard/deployment/
```

- Define Elastissearch credentails and create Kubernetes secret from them
```
export ELASTIC_USER=elastic && export ELASTIC_PASSWD=Seagate123
kubectl create ns elastic
kubectl create secret generic dashboard-elasticsearch-es-elastic-user --from-literal=$ELASTIC_USER=$ELASTIC_PASSWD -n elastic
``

- Deploy Elasticsearch and Kibana stack.
```
pushd elasticsearch && kubectl apply -f . -R && popd
```

- Validate that Elasticsearch and Kibana stack deployed without any issues. Use below command and wait till Health column is showing green.
```
[root@ssc-cicd-3176 deployment]#  kubectl get elasticsearch -n elastic -w
NAME                      HEALTH    NODES   VERSION   PHASE             AGE
dashboard-elasticsearch   unknown           8.4.1     ApplyingChanges   11s
dashboard-elasticsearch   unknown   1       8.4.1     ApplyingChanges   29s
dashboard-elasticsearch   unknown   2       8.4.1     ApplyingChanges   30s
dashboard-elasticsearch   unknown   3       8.4.1     ApplyingChanges   31s
dashboard-elasticsearch   unknown   3       8.4.1     ApplyingChanges   36s
dashboard-elasticsearch   unknown   3       8.4.1     ApplyingChanges   36s
dashboard-elasticsearch   unknown   3       8.4.1     ApplyingChanges   37s
dashboard-elasticsearch   green     3       8.4.1     Ready             38s
```

- Deploy Dashboard Application 
```
kubectl create ns dashboard &&  pushd dashboard && kubectl apply -f . -R && popd
```

## Deploy Dashboard Application on Kubernetes Cluster using Argocd

### Argocd

- We will use ArgoCD to deploy Dashbaord Application. ArgoCD is a GitOps Tool used for deploying applications in Kubernetes cluster. 
- Refer - https://argo-cd.readthedocs.io/en/stable/ 

### Argocd deployment 

- ArgoCD itself is an K8s based Application and need to be deployed in K8s cluster. Install ArgoCD using [ArgoCD Installation](https://argo-cd.readthedocs.io/en/release-1.8/getting_started/#1-install-argo-cd)
- We will use NodePort of `argocd-server` service to access ArgoCD application. Use below command to get Port. Access ArgoCD UI using `https://<VM IP>:<NodePort>`
```
kubectl get ep argocd-server -n argocd
```  
- Login to system using user `admin` and use below command to fetch default password. 
```  
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo
```
- Install ArgoCD CLI tools using [ArgoCD CLI installation](https://argo-cd.readthedocs.io/en/release-1.8/cli_installation/)

### Dashboard Application Deployment through Argocd

- Login to ArgoCD through CLI and server details as `<VM IP>:<NodePort>`
```
argocd login `<VM IP>:<NodePort>` --insecure
Username: admin
Password:
'admin:login' logged in successfully
```
- Create ElasticSearch Application using CLI
```
argocd app create elasticsearch \
--repo https://github.com/shailesh-vaidya/cortx-re \
--revision argo-codacy \
--path ./solutions/dashboard/deployment/elasticsearch \
--dest-namespace elastic \
--dest-server https://kubernetes.default.svc \
--directory-recurse \
--sync-policy auto
```
- Create Dashboard application using CLI
```
argocd app create dashboard \
--repo https://github.com/shailesh-vaidya/cortx-re \
--revision dashboard-cd \
--path ./solutions/dashboard/deployment/dashboard \
--dest-namespace dashboard \
--dest-server https://kubernetes.default.svc \
--directory-recurse \
--sync-policy auto
```

- Validate that both `elasticsearch` and `dashboard` applications are healthy in Argo CD UI at `http://<VM IP>:<Argo CD NodePort>`
- Visit Kibana Dashboad at `http://<VM IP>:30081` and login using username as `elastic`. Use below command to fetch password. 
```
kubectl get secret dashboard-elasticsearch-es-elastic-user -o go-template='{{.data.elastic | base64decode}}' -n elastic
```

 


