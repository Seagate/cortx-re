# Deploy Dashboard Application on Kubernetes Cluster using Argocd

The following sections discusses setps to set up the K8s cluster and deploy the Dashbaord Application. Plese setup 3 Node Kubernetes cluster with below specifications. The minimum prerequisites are enlisted to make sure the cluster set up process is smooth

## Prerequisites

Following are the minimum system specifications required to set up the K8s cluster and Dashbaord Application. 

-  RAM: 16GB
-  CPU: 8Core
-  DISK: 9 Disks (1 with 50GB (For operating system) and rest 8 with 25GB per disk)
-  OS: CentOS 7.9 (64-bit)

## Kubernetes Cluster Setup

-  Please follow steps from [Kubernetes Installation](../../solutions/community-deploy/CORTX-Deployment.md#install-k8s-cluster) to setup Kubernetes cluster. 

## Argocd

- We will use ArgoCD to deploy Dashbaord Application. ArgoCD is aGitOps Tool used for deploying applications in Kubernetes cluster. 
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

## Dashboard Application deployment through Argocd

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

 


