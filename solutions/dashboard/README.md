## Table of Contents

- [Dashboard](#dashboard)
- [Directory Structure](#directory-structure)
- [Components](#components)
  - [Storage Class](#storage-class)
  - [Namespace](#namespace)
  - [Elasticsearch and Kibana](#elasticsearch-and-kibana)
  - [Dashboard Configmap](#dashboard-configmap)
  - [Dashboard Secret](#dashboard-secret)
  - [MongoDB](#mongodb)
  - [Port Scanner](#port-scanner)
  - [Codacy](#codacy)
  - [GitHub](#github)
  - [Jenkins](#jenkins)
  - [Logstash](#logstash)
- [Kubectl Installation](#kubectl-installation)

<a name="dashboard" />

## Dashboard

Creating a dashboard to verify the status of tools like Port Scanner, Codacy, GitHub and Jenkins. There will be one Main Dashboard showing the status of multiple tools. When you click on the tool then another dashboard will open where you can find detailed data and analysis of that tool.

<a name="directory-structure" />

## Directory Structure

```
dashboard
├─── build
├─── config
├─── deployment
└─── tools
```

### config

This directory contains the YAML files of the tools like portscanner, codacy, etc. It also contains the YAML to create a dashboard secret and configmap.

### tools

This directory contains the actual code of PortScanner, Codacy, GitHub and Jenkins.

<a name="components" />

## Components

<a name="storage-class" />

### Storage Class

A StorageClass is a Kubernetes storage mechanism that lets you dynamically provision persistent volumes (PV) in a Kubernetes cluster. In dashboard, the Elasticsearch and MongoDB creates the PV for storing the data.

The **local-path-provisioner** is default storage class.

<a name="namespace" />

### Namespace

We have seperated the dashboard in different namespace depending on the use and role of that component.

We need following namespaces:

- elastic-system: For ECK operator
- elastic: For Elasticsearch and Kibana
- dashboard: For other dashboard components

<a name="elasticsearch-and-kibana" />

### Elasticsearch and Kibana

Elasticsearch is a distributed search and analytics engine. It allows you to store, search, and analyze huge volumes of data quickly in near-real-time. We are primarily using it to store the tools data from MongoDB.

Kibana is a data visualization and exploration tool used for log and time-series analytics. It is an offical interface of Elasticsearch. We are using it to create the dashboard for the tools data.

<a name="dashboard-configmap" />

### Dashboard Configmap

Configmap is used to store the configuration data. You need to enter the configuration data in **DashboardConfigmap.yaml** file located in **dashboard/config** directory. For configmap, do not convert data into base64 format and add it in plain text.

The requirement of the configuration is depending on the use of Tool. If you don't want to use the perticular tool, then don't need to enter the configuration for that tool.

| Configuration           | Value Description                 | Default                                             | Components using configurations | Required |
| ----------------------- | --------------------------------- | --------------------------------------------------- | ------------------------------- | -------- |
| mongodb_connection_url  | URL of MongoDB                    | dashboard-mongodb.dashboard.svc.cluster.local:27017 | Logstash, Tools                 | Yes      |
| master_node_internal_ip | Master node IP                    | -                                                   | Logstash                        | Yes      |
| elasticsearch_nodeport  | Nodeport of Elasticsearch Service | -                                                   | Logstash                        | Yes      |
| secret_name             | Name of dashboard secret          | dashboard-secret                                    | Port Scanner                    | No       |
| github_api_baseurl      | Baseurl of Github API             | https://api.github.com                              | GitHub Tool                     | No       |
| jenkins_server_url      | URL of Jenkins server             | -                                                   | Jenkins Tool                    | No       |

<a name="dashboard-secret" />

### Dashboard Secret

The secret is used to store the credentials. You need to provide the credentials data in **DashboardSecret.yaml** file located in **dashboard/config** directory. Please provide all the values in base64 format.

The requirement of the credential is depending on the use of Tool. If you don't want to use the perticular tool, then don't need to enter the credential for that tool.

| Credential             | Value Description          | Default | Components using credentials | Required |
| ---------------------- | -------------------------- | ------- | ---------------------------- | -------- |
| mongodb_username       | Username for MongoDB       | -       | MongoDB, Logstash, Tools     | Yes      |
| mongodb_password       | Password for MongoDB       | -       | MongoDB, Logstash, Tools     | Yes      |
| elasticsearch_username | Username for Elasticsearch | -       | Logstash                     | Yes      |
| elasticsearch_password | Password for Elasticsearch | -       | Logstash                     | Yes      |
| logstash_password      | Password for Logstash      | -       | Logstash                     | Yes      |
| codacy_api_token       | Token of Codacy            | -       | Codacy Tool                  | No       |
| github_token           | Token of Github            | -       | GitHub Tool                  | No       |
| jenkins_username       | Username of Jenkins server | -       | Jenkins Tool                 | No       |
| jenkins_token          | Token of Jenkins server    | -       | Jenkins Tool                 | No       |

<a name="mongodb" />

### MongoDB

MongoDB is used to store the data of dashboard tools. The support for MongoDB interaction is provided for individual tool. After staring the tool, it will create database and collection inside those database for data storage.

| Tool         | Database Name | Collections Count | Collection Names                                               |
| ------------ | ------------- | ----------------- | -------------------------------------------------------------- |
| Port Scanner | portscanner   | 1                 | operator                                                       |
| Codacy       | codacy        | 2                 | metadata, repositories                                         |
| Github       | github        | 4                 | github_repo, github_meta, github_branches, github_contributors |
| Jenkins      | jenkins       | 3                 | jenkins_jobs, jenkins_jobs, jenkins_builds                     |

Requirements:

- Dashboard Secret: For MongoDB username and password

<a name="port-scanner" />

### Port Scanner

Port Scanner is a Kubernetes operator that keeps track of the services. It extracts the ports used by the services and checks whether the port is allowed to open or not. If the port is not allowed then then it should flag the service and mark the namespace in non-compliance state.

Requirements:

- CRD: It defines CR’s fields and what type of values those fields contain
- CR:
  These contain the values of the attributes whose schema is defined in the CRD. Fields are present in below table:

  | Field Name    | Value Type        | Value Description                                      | Required |
  | ------------- | ----------------- | ------------------------------------------------------ | -------- |
  | namespace     | string            | The namespace in which Port Scanner is deployed        | Yes      |
  | scanNamespace | string            | The namespace in which Port Scanner need to keep track | Yes      |
  | allowedPorts  | array of interger | Array of ports that services allowed to use            | Yes      |
  | scanObject    | string            | The object on which operator need to keep track        | Yes      |

- Service Account, ClusterRole, and ClusterRoleBinding: We need these resources because Port Scanner need to keep track on another object and the object can be in another namespace

- Configmap: Automatically created by Port Scanner containing the allowed ports

- Dashboard Configmap: For secret name and MongoDB URL

- Dashboard Secret: For MongoDB username and password

- Deployment: For execution of port scanner

<a name="codacy" />

### Codacy

Codacy is a static code analysis tool. It automates code reviews and monitors code quality. It also provides the API which allows us to programmatically retrieve and analyze data from Codacy.

The Codacy tool script will connect with the Codacy for fetching the data and store that data into the MongoDB. Visualization are performed on the collected data.

Requirements:

- Dashboard Configmap: For MongoDB url

- Dashboard Secret: For username and password of MongoDB and for Codacy API token

- CronJob: For execution of Codacy script

<a name="github" />

### GitHub

GitHub is a code hosting platform for version control and collaboration. It's used for storing, tracking, and collaborating on software projects.

The GitHub tool script will connect with the GitHub server for fetching the data and store that data into the MongoDB. Visualization are performed on the collected data.

Requirements:

- Dashboard Configmap: For MongoDB url and GitHub API baseurl

- Dashboard Secret: For username and password of MongoDB and for GitHub API token

- CronJob: For execution of GitHub script

<a name="jenkins" />

### Jenkins:

Jenkins is an open-source continuous integration/continuous delivery and deployment (CI/CD) automation software DevOps tool written in the Java programming language. It is used to implement CI/CD workflows, called pipelines.

The Jenkins tool script will connect with the Jenkins server for fetching the data and store that data into the MongoDB. Visualization are performed on the collected data.

Requirements:

- Dashboard Configmap: For MongoDB URL and Jenkins server URL

- Dashboard Secret: For username and password of MongoDB and for Jenkins username and token

- CronJob: For execution of Jenkins script

<a name="logstash" />

### Logstash:

Logstash is an open-source data ingestion tool that allows you to collect data from a variety of sources, transform it, and send it to your desired destination.

For dashboard logstash is used to collect the data from MongoDB, perform some transformation and then store that data into the Elasticsearch.

Requirements:

- Dashboard Configmap: For MongoDB URL, master node ip and Elasticsearch port

- Dashboard Secret: For username and password of MongoDB, username and password of Elasticsearch and logstash password

- CronJob: For execution of logstash pipeline

<a name="kubectl-installation" />

## Kubectl Installation

For installation using the kubectl please goto the **dashboard/config** directory

### Storage Class

The Elasticsearch and MongoDB creates the PV for data storage. For that you need to create the local-path storage class. Please install it using this reference https://github.com/rancher/local-path-provisioner

### Elasticsearch and Kibana

For this use the Elastic Cloud on Kubernetes (ECK). By using this you can install Elasticsearch and Kibana on Kubernetes.
Please use **8.4.1** version of Elasticsearch and Kibana. You also need to access them from internet.

Please open **ElasticsearchKibanaInstallation.md** for installation steps.
Link: https://github.com/Seagate/cortx-re/blob/dashboard/solutions/dashboard/ElasticsearchKibanaInstallation.md

### Namesapce

To create the dashboard and elastic namespace use the following commands:

```
kubectl create namespace elastic
kubectl create namespace dashboard
```

### Secret

Provide the credentials in the DashboardSecret.yaml file. For that you need to provide **base64** values. For converting plain text in base64 format use the following command:

```
echo -n <plain_text> | base64
```

**Note**:
If you have not cleaned old PVC of mongodb **data-dashboard-mongodb-0** then please set the same old username and password for MongoDB in base64 format.

For **elasticsearch** credentials, the username will be **elastic** but you need to take password using following commands (You need to convert this password into **base64** format):

```
PASSWORD=$(kubectl get secret dashboard-elasticsearch-es-elastic-user -o go-template='{{.data.elastic | base64decode}}' -n elastic)
echo $PASSWORD
```

You can create the secret by following command:

```
kubectl create -f ./DashboardSecret.yaml
```

### Configmap

Please enter all values in plain text format.

You can create the configmap by following command:

```
kubectl create -f ./DashboardConfigmap.yaml
```

### MongoDB

For creating the MongoDB run the following command:

```
kubectl create -f ./mongodb/mongodb.yaml
```

After executing this command, you will see the MongoDB statefulset and the headless service.
The MongoDB statefulset will try to provision Persistent Volume (PV) for it.

You can exec into mongodb pod using following command:
Please replace **\<username>** by your mongodb_username value in plain text.

```
kubectl exec -it pod/dashboard-mongodb-0 -n dashboard -  /bin/bash
mongosh --username=<username>
```

### Port Scanner

For creating port scanner run the following command:

```
kubectl create -f ./portscanner/PortScannerCRD.yaml
kubectl create -f ./portscanner/PortScannerCR.yaml
kubectl create -f ./portscanner/PortScannerAccount.yaml
kubectl create -f ./portscanner/PortScannerDeployment.yaml
```

Once the port scanner started you can verify the logs of the pod. For that you can use following command:
Please replace <pod_name> here by actual pod name

```
kubectl logs -f pod/<pod_name> -n dashbaord
```

From the logs you can find whether the MongoDB connection is made or not and whether documents are inserted into mongodb or not.

**Note on Port Scanner Logs**
You will find below exception in logs.

```
Exception: HTTPSConnectionPool(host='10.96.0.1', port=443): Read timed out.
```

This exception will raise after every 60 seconds when the operator is idle. This is because the client will try to re-create the connection with the server.

### Codacy

For creating codacy run the following command:

```
kubectl create -f ./codacy/CodacyCronjob.yaml
```

By default the Cronjob will schedule at 00:00 on every Saturday. If you need to change the schedule, then you can do that by modifying into **config/codacy/CodacyCronjob.yaml**. If you wanted to the job immediately after starting the cronjob use the following command:

```
kubectl create job --from=cronjob/dashboard-codacy dashboard-codacy-manual-001 -n dashboard
```

You can check the codacy logs using following command:
Please replcae <pod_name> by actual pod name

```
kubectl logs -f pod/<pod_name> -n dashboard
```

The codacy script should connect with mongodb and start fetching the data from codacy.

### GitHub

For creating github run the following command:

```
kubectl create -f ./github/GithubCronjob.yaml
```

By default the Cronjob will schedule at 00:00 on every Saturday. If you need to change the schedule, then you can do that by modifying into **config/github/GithubCronjob.yaml** If you wanted to the job immediately after starting the cronjob use the following command:

```
kubectl create job --from=cronjob/dashboard-github dashboard-github-manual-001 -n dashboard
```

You can check the github logs using following command:
Please replcae <pod_name> by actual pod name

```
kubectl logs -f pod/<pod_name> -n dashboard
```

The github script should connect with mongodb and start fetching the data from github.

### Jenkins

For creating jenkins run the following command:

```
kubectl create -f ./jenkins/JenkinsCronjob.yaml
```

By default the Cronjob will schedule at 00:00 on every Saturday. If you need to change the schedule, then you can do that by modifying into **config/jenkins/JenkinsCronjob.yaml**. If you wanted to the job immediately after starting the cronjob use the following command:

```
kubectl create job --from=cronjob/dashboard-jenkins dashboard-jenkins-manual-001 -n dashboard
```

You can check the jenkins logs using following command:
Please replcae <pod_name> by actual pod name

```
kubectl logs -f pod/<pod_name> -n dashboard
```

The jenkins script should connect with mongodb and start fetching the data from jenkins.

### Logstash

**Note:**
The pre-requisite for logstash is mongodb and elasticsearch instance should be running. Because, the logstash pipeline is taking data from MongoDB and sending it to Elasticsearch

For creating logstash tun the following command:

```
kubectl create -f ./logstash/LogstashConfigmap.yaml
kubectl create -f ./logstash/LogstashCronjob.yaml
```

You can check logstash logs using following command:
Please replcae <pod_name> by actual pod name

```
kubectl logs -f pod/<pod_name> -n dashboard
```

By default the Cronjob will schedule after every 2 hours. If you need to change the schedule, then you can do that by modifying into **config/logstash/LogstashCronjob.yaml** If you wanted to the job immediately after starting the cronjob use the following command:

```
kubectl create job --from=cronjob/dashboard-logstash dashboard-logstash-manual-001 -n dashboard
```

After successful execution of logstash pod, you should be able to see the mongodb documents in lostash logs, and in the Kibana GUI, you should be able to see indices.

To check indices in Kibana,

- **Login** into Kibana
- Open left side menu
- Goto Management > Stack Management
- Click on **Index Management** in left menu.
  You should be able to see the indices.
