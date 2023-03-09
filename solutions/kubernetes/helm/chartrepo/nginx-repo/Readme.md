# NGINX Helm Repository

This document covers instructions to set up and configure the local Helm repository using NGINX.

- [Prerequisites](#prerequisites)
- [Setup NGINX server](#setup-nginx-server)
- [Create Helm Repository](#create-helm-repository)
- [Use Helm Repository](#using-helm-repository)
- [Add new chart to existing Helm repository](#adding-new-chart)
- [Update Helm repository](#update-helm-repository)

<a name="prerequisites" />

## Prerequisites

#### **Docker Compose**

Please check whether docker-compose is exist in your system. You can verify that using below command.

```
docker-compose version
```

Please use below commands to install docker-compose. Please check the latest version while installation.

```
curl -SL https://github.com/docker/compose/releases/download/v2.16.0/docker-compose-linux-x86_64 -o /usr/local/bin/docker-compose
chmod +x /usr/local/bin/docker-compose
```

#### Helm Installation

Please check whether helm is exist in your system. You can verify that using below command.

```
helm version
```

Please use the below steps for installing helm if not already installed. You can also take reference from https://helm.sh/docs/intro/install/#from-script.

```
curl -fsSL -o get_helm.sh https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3
chmod 700 get_helm.sh
./get_helm.sh
```

<a name="setup-nginx-server" />

## Setup NGINX server

1. Clone the “dashboard” branch from “cortx-re” repository

```
git clone https://github.com/Seagate/cortx-re.git -b dashboard
```

2. Goto **nginx-repo** directory

```
cd cortx-re/solutions/kubernetes/helm/chartrepo/nginx-repo
```

3. Create directories for volume mapping with NGINX server

```
mkdir -p /nginx/conf /nginx/repos
```

4. Copy default.conf file to **/nginx/conf** directory

```
cp default.conf /nginx/conf
```

5. Start the NGINX server
   By default below configuration applied:
   Port: **30080**
   Volumes: **/nginx/conf/**, **/nginx/repos/**

   If you want to modify the configuration then please update the **docker-compose.yaml** file.

```
docker-compose up -d
```

Open the http://\<host_url>:\<port> in the browser and check the directory structure.

<a name="create-helm-repository" />

## Create Helm Repository

1. Create **chartrepo** and **chartrepo/dashboard-charts** directories under **/nginx/repos/**

```
mkdir -p /nginx/repos/chartrepo /nginx/repos/chartrepo/dashboard-charts
```
#### Package the Chart

For packaging the chart we will take example of Helm chart for DevOps Dashboard project.

**Note:** You can also use your own chart

1. Go to the “cortx-re/solutions/dashboard/charts” directory.

```
cd cortx-re/solutions/dashboard/charts
```

2. Package dashboard-project helm chart

```
helm package dashboard-project/
```

3. Package elastic-project helm chart

```
helm package elastic-project/
```

4. Copy files to repository

```
cp *.tgz /nginx/repos/chartrepo/dashboard-charts
```

#### Generate **index.yaml**

To generate the index.yaml file goto **chartrepo** directory and run below command.

In our case the \<port> will replace by **30080**.

```
cd /nginx/repos/chartrepo
helm repo index . --url http://<host_url>:<port>/chartrepo
```

Check the generated index.yaml file. All charts will be added there with the URL.

#### **Final Directory Structure**

The final Helm repository structure will look like

```
chartrepo
├─── index.yaml
├─── dashboard-charts
  └─── dashboard-project-0.1.0.tgz
  └─── elastic-project-0.1.0.tgz
```

<a name="using-helm-repository" />

## Use the Helm Repository

Replace the \<repo_name> and \<chart_name> by other values.

- Add the repo in helm

  ```
  helm repo add \<repo_name> http://<host_url>:<port>/chartrepo
  ```

- List the helm repos

  ```
  helm repo list
  ```

- Search the chart

  ```
  helm search repo <chart_name>
  ```

  OR

  ```
  helm search repo <repo_name>/<chart_name>
  ```

- Get the values from chart

  ```
  helm show values <repo_name>/<chart_name>
  ```

  Add the values in file. You can modify the values from and pass it to chart while installation.

  ```
  helm show values <repo_name>/<chart_name> > <file_name>.yaml
  ```

- Install the chart

  ```
  helm install <name> <repo_name>/<chart_name>
  ```

  Passing values.yaml while installing chart

  ```
  helm install <name> <repo_name>/<chart_name> --values <file_name>.yaml
  ```

<a name="adding-new-chart" />

## Add new chart to existing Helm repository

#### Modifying Directory Structure

Modify the existing directory structure and add the **.tgz** file of new chart.

The modified directory structure looks like below example:

```
chartrepo
├─── index.yaml
├─── dashboard-charts
  └─── dashboard-project-0.1.0.tgz
  └─── elastic-project-0.1.0.tgz
└─── sample-charts
  └─── demo-app-1-0.1.0.tgz
```

#### Regenerate the index.yaml file

After modifying directory structure and adding new charts regenerate the index.yaml file.

Note: Everytime you add or remove chart from repository then please regenerate the index.yaml file.

Command:

```
cd /nginx/repos/chartrepo
helm repo index . --url http://<host_url>:<port>/chartrepo
```

<a name="update-helm-repository" />

## Update Helm repository

This changes is needed while using the chart from Helm Repository.

#### **Update the added repository**

Update the repository using below command:

```
helm repo update <repo_name>
```

#### **Remove and add the repository**

Try removing repo and adding it again in case of update failure.

Command to remove helm repository
```
helm repo remove <repo_name>
```

Command to add helm repository
```
helm repo add <repo_name> http://<host_url>:<port>/chartrepo
```
