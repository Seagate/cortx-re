# Setup K8s Cluster and Deploy CORTX Stack

The following sections discusses how to set up the K8s cluster and deploy the CORTX Stack. The minimum prerequisites are enlisted to make sure the cluster set up process is smooth.

## Prerequisites
Following are the minimum system specifications required to set up the K8s cluster and deploy the CORTX stack.

-  RAM: 16GB
-  CPU: 8Core
-  DISK: 9 Disks (1 with 50GB (For operating system) and rest 8 with 25GB per disk)
-  OS: CentOS 7.9 (64-bit)

## Deploy the CORTX Stack on K8s cluster
This section enlists the commands to deploy the CORTX Stack on K8s cluster. 

### Make sure your VM(virtual machine) has following drives available on it:
```
ls /dev/sd*

/dev/sda  /dev/sda1  /dev/sda2  /dev/sdb  /dev/sdc  /dev/sdd  /dev/sde  /dev/sdf  /dev/sdg  /dev/sdh  /dev/sdi
```

### You must have Git installed on your system.
You can use following command for RedHat family OS to install the Git.
```
yum install git -y
```

### SELinux should be disabled
-  Use the following command to check status of SELinux.
```
sestatus
```
-  If SELinux is enabled, run the following command to disable the SELinux.
```
sed -i 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config && setenforce 0
```
-  Once above command runs successfully, reboot your system.
```
reboot
```   

**Note:**
 1. All the nodes should be reachable over SSH
 2. You can deploy CORTX on AWS EC2 instance also. Please follow [CORTX Deployment on AWS](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/cloud/AWS/README.md)

## Install K8s cluster
**To install the K8s cluster, run the following commands:**
-  Clone cortx-re repository and change directory to `cortx-re/solutions/kubernetes`.
```
git clone https://github.com/Seagate/cortx-re && cd $PWD/cortx-re/solutions/kubernetes
```
-  Create the hosts file in the current directory to add entries for all the nodes with same format in hosts file. Node from first entry will be configured as Primary node. Example `hosts` file for multi-node setup is as below,
```
hostname=cortx-deploy-node1.cortx.com,user=root,pass=<root-password>
hostname=cortx-deploy-node2.cortx.com,user=root,pass=<root-password>
hostname=cortx-deploy-node3.cortx.com,user=root,pass=<root-password>
```
-  Execute `cluster-setup.sh` to setup K8s cluster on your EC2 instances for deployment.
-  To allow the PODs creation on primary node, pass the first input parameter for `cluster-setup.sh` script as `true`. Please note you must pass the input parameter as true for multi-node setup.
```
./cluster-setup.sh true
```

## Deploy CORTX Stack 
- Execute `cortx-deploy.sh` to deploy the CORTX stack on your K8s cluster.
```
export SOLUTION_CONFIG_TYPE=automated && ./cortx-deploy.sh --cortx-cluster
```

**Note:**  
- Following parameter/s are passed when the cluster deployment command executes. If no parameter is passed, the default ones are chosen.

| Parameter     | Default value     | Description     |
| :------------- | :----------- | :---------|
| CORTX_SCRIPTS_BRANCH      | v0.6.0  | If you want to use another cortx-K8s branch then export this variable with your branch.     |
| CORTX_SCRIPTS_REPO | Seagate/cortx-k8s | If you want to use another cortx-K8s repo (like your fork), export this variable with your repo. |
| CORTX_SERVER_IMAGE | ghcr.io/seagate/cortx-rgw:2.0.0-latest | Also, if you want to use different server image then export this variable with new image. |
| CORTX_DATA_IMAGE | ghcr.io/seagate/cortx-data:2.0.0-latest | Also, if you want to use different data image then export this variable with new image. |
| CORTX_CONTROL_IMAGE | ghcr.io/seagate/cortx-control:2.0.0-latest | Also, if you want to use different control image then export this variable with new image. |
| DEPLOYMENT_METHOD | standard | CORTX supports two deployment methods `Standard` will deploy full cluster and `data-only` will deploy on CORTX data pods. |
| CONTROL_EXTERNAL_NODEPORT | 31169 | If you want to use different port for control service, export this variable with another port. |
| S3_EXTERNAL_HTTP_NODEPORT | 30080 | If you want to use different port for HTTP Port to IO service, then export this variable with another port. |
| S3_EXTERNAL_HTTPS_NODEPORT | 30443 | If you want to use different port for HTTPS Port to IO service, then export this variable with another port. |
| SNS_CONFIG | 1+0+0 | SNS configuration for deployment. Please select value based on disks available on nodes. |
| DIX_CONFIG | 1+0+0 | DIX configuration for deployment. Please select value based on disks available on nodes. |
| NAMESPACE  | default | Kubernetes cluster Namespace for CORTX deployments. |
| SOLUTION_CONFIG_TYPE | manual | There are two config types for solution.yaml file; manual and automated. In automated type the solution.yaml is created by script if VM is created as per standard specification. In manual type the user needs to create solution.yaml with required disks, image details etc.; place it at script location and configure SOLUTION_CONFIG_TYPE variable as manual. |

**For example:**
```
export CORTX_SCRIPTS_BRANCH=integration && export CORTX_SCRIPTS_REPO=Seagate/cortx-k8s && export SOLUTION_CONFIG_TYPE=automated && ./cortx-deploy.sh --cortx-cluster
```

## Sanity test 
- Run IO Sanity on your CORTX Cluster to validate bucket creation and object upload in deployed cluster.
```
./cortx-deploy.sh --io-sanity
```

Tested by:

* July 26, 2022: Mukul Malhotra (mukul.malhotra@seagate.com) - AWS EC2, CentOS 7.9 Linux
* May 06, 2022: Rahul Shenoy (rahul.shenoy@seagate.com) - Windows , VMware Workstation 16 , CentOS 7.9 Linux
