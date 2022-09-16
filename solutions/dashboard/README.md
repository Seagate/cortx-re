## Dashboard

We are creating a dashboard where we can verify the status of different tools like Port Scanner, and Codacy. There will be one Main Dashboard showing the status of multiple tools. When we click on the tool then another dashboard will open where we can find detailed data and analysis of that tool.

## Directory Structure:

```
dashboard
├─── config
├─── tools
└─── scripts
```

#### config

This directory contains the YAML files of the tools like portscanner, codacy, etc. It also contains the YAML to create a secret and configmap.

#### tools

This directory contains the actual code of portscanner and codacy.

#### scripts

This directory contains the creation and cleanup scripts. The cleanup script does not clean the Persistent Volume (PV) of MongoDB.

## Installations

### Install Elasticsearch and Kibana:

For this use the Elastic Cloud on Kubernetes (ECK). By using this we can install Elasticsearch and Kibana on Kubernetes.
Please use **8.4.1** version of Elasticsearch and Kibana. We also need to access them from internet.

Please open **ELASTICSEARCH_KIBANA_INSTALLATION.md** for installation steps.
Reference Link: https://www.elastic.co/guide/en/cloud-on-k8s/current/k8s-quickstart.html

### Creating Dashboard Secret and Configmap:

#### Secret:

The secret is used to store the credentials. You need to provide the credentials data in **DashboardSecret.yaml** file located in **dashboard/config** directory.

For the secret, you need to provide **base64** values. For converting plain text in base64 format use the following command:

```
echo -n plain_text | base64
```

**Note**:
While setting MongoDB **username** and **password**. If you have not cleaned old PVC of mongodb **data-cortx-port-scanner-mongodb-0** then please set the same old password in base64 format.

For **elasticsearch** credentials, the username will be **elastic** but you need to take password using following commands:

```
PASSWORD=$(kubectl get secret cortx-elasticsearch-es-elastic-user -o go-template='{{.data.elastic | base64decode}}')
echo $PASSWORD
```

You need to convert this password into **base64** format

#### Configmap:

Configmap is used to store the configuration data. You need to enter the configuration data in **DashboardConfigmap.yaml** file located in **dashboard/config** directory. Do not convert data into base64 format and add it in plain text.

**Configmap Fields:**

- **master_node_internal_ip**
  You can find the primary node internal ip using following command:

```
kubectl get nodes -o wide
```

- **elasticsearch_nodeport**
  You can find the nodeport using following command:

```
kubectl get service cortx-elasticsearch-es-http
```

#### Creation

You can create the Secret and Configmap in 2 ways:

- Goto **dashboard/config** directory

```
kubectl create -f DashboardSecret.yaml
kubectl create -f DashboardConfigmap.yaml
```

- Goto **dashboard/scripts** directory
  The script contains the above commands so that you don't need to manually type all the commands.You need to run the script from **dashboard/scripts** directory only. If you execute this from some other directory then it will not work.
  Run the following command:

```
python3 dashboard_configs_creation.py
```

### Creating Port Scanner:

For creating port scanner goto **dashboard/scripts** directory and run the following command:

```
python3 portscanner_creation.py
```

The portscanner operator wrote in such a way that it will create the required components such as ConfigMap, MongoDB by itself.

Once the port scanner started you can verify the logs of the pod. For that you can use following command:
Please replace <pod_name> here by actual pod name

```
kubectl logs -f po/<pod_name>
```

From there you can find whether the MongoDB connection is made or not and whether documents are inserted into mongodb or not.

**MongoDB:**
You will also see the MongoDB statefulset and the headless service.
The MongoDB statefulset will try to provision Persistent Volume (PV) for it.

You can exec into mongodb pod using following command:
Please replace \<username> by your mongodb_username value in plain text.

```
kubectl exec -it po/cortx-port-scanner-mongodb-0 -- sh
mongosh --username=<username>
```

**Note on Port Scanner Logs**
You will find below exception in logs.

```
Exception: HTTPSConnectionPool(host='10.96.0.1', port=443): Read timed out.
```

This exception will raise after every 60 seconds when the operator is idle. This is because the client will try to re-create the connection with the server.

### Creating Codacy:

**Note:**
Currently, we are creating mongodb from the port scanner itself. So, the pre-requisite for codacy is portscanner should be created and mongodb instance should be running.

**Creation**
For creating codacy goto **dashboard/scripts** directory and run the following command:

```
python3 codacy_creation.py
```

You can check the codacy logs using following command:
Please replcae <pod_name> by actual pod name

```
kubectl logs -f po/<pod_name>
```

The codacy script should connect with mongodb and start fetching the issues data from codacy. The script will pause after fetching all data and restarts after every 24 hours. This will not cause the pod to restart.

### Creating Logstash:

**Note:**
The pre-requisite for logstash is mongodb and elasticsearch instance should be running. Because, the logstash pipeline is taking data from MongoDB and sending it to Elasticsearch

For creating logstash goto **dashboard/scripts** directory and execute the following command:

```
python3 logstash_creation.py
```

You can check logstash logs using following command:
Please replcae <pod_name> by actual pod name

```
kubectl logs -f po/<pod_name>
```

The logstash should start installing **logstash-input-mongodb** plugin and should start the pipeline. After this, you should be able to see the mongodb documents in lostash logs, and in the Kibana GUI, you should be able to see indices.

To check indices in Kibana,

- **Login** into Kibana
- Open left side menu
- Goto Management > Stack Management
- Click on **Index Management** in left menu.
  You should be able to see codacy_metadata, codacy_repositories and
  portscanner_operator indices.
