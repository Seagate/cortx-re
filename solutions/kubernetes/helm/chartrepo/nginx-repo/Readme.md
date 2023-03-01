# NGINX Helm Repository

This document covers how to create and configure local Helm repository using NGINX.

- [Prerequisites](#prerequisites)
- [Package the Chart](#packaging-chart)
- [Create Helm Repository](#create-helm-repository)
- [Start NGINX server](#starting-nginx-server)
- [Use the Helm Repository](#using-helm-repository)
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

#### **Volumes**

For this setup two volumes are required:

- Configuration Volume:

  - This volume is to override the default configuration of NGINX.
  - The default location if set to **/nginx/conf**.
  - You can override that in docker-compose.yaml file.
  - Copy the default.conf file to that location

- Repository Volume:
  - This volume to add the actual Helm charts and serve them through NGINX.
  - The default location is **/nginx/repos**.
  - You can override that in docker-compose.yaml file.

<a name="packaging-chart" />

## Package the Chart

For packaging the chart we will take example of Helm chart for DevOps Dashboard project.
**Note:** You can also use your own chart

1. Clone the “dashboard” branch from “cortx-re” repository.

```
git clone https://github.com/Seagate/cortx-re.git -b dashboard
```

2. Go to the “cortx-re/solutions/dashboard/charts” directory.

```
cd cortx-re/solutions/dashboard/charts
```

3. Package dashboard-project helm chart

```
helm package dashboard-project/
```

4. Package elastic-project helm chart

```
helm package elastic-project/
```

Step 3 and 4 will generate **.tgz** file and these files we will put in the repository.

<a name="create-helm-repository" />

## Create Helm Repository

Create the directory structure in below format in the **Repository Volume** mentioned above.

```
chartrepo
├─── index.yaml
├─── chart_category_name
  └─── chart_package.tgz
```

#### **chart_category_name**

Create the directory named as the chart category like dashboard-charts, sample_charts, etc.

#### **chart_package.tgz**

This is the file generated after packaging the chart. The files mentioned in the above example.

#### **index.yaml**

To generate the index.yaml file goto **chartrepo** directory and run below command.

In our case the \<port> will replace by **30080**.

```
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

<a name="starting-nginx-server" />

## Start NGINX server

Please use below command to start the NGINX server.

```
docker-compose up -d
```

Open the \<host_url>:<port>/chartrepo in the browser and check the directory structure.

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

If the update not worked then remove the repository and add it again

```
helm repo remove <repo_name>
helm repo add \<repo_name> http://<host_url>:<port>/chartrepo
```
