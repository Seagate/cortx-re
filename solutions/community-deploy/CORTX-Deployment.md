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
# ls /dev/sd*

/dev/sda  /dev/sda1  /dev/sda2  /dev/sdb  /dev/sdc  /dev/sdd  /dev/sde  /dev/sdf  /dev/sdg  /dev/sdh  /dev/sdi
```

### You must have Git installed on your system.
You can use following command for RedHat family OS to install the Git.

```
# yum install git -y
```

### SELinux should be disabled

-  Use the following command to check status of SELinux.
```
# sestatus
```
-  If SELinux is enabled, run the following command to disable the SELinux.

```
# sed -i 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config && setenforce 0
```

-  Once above command runs successfully, reboot your system.

```
# reboot
```   

### Note
 1. All the nodes should be reachable over SSH with root user.
 2. You can deploy CORTX on AWS EC2 instance also. Please follow [CORTX Deployment on AWS](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/cloud/AWS/README.md)

## Install K8s cluster
**To install the K8s cluster, run the following commands:**

-  Clone cortx-re repository and change directory to `cortx-re/solutions/kubernetes`.
```
# git clone https://github.com/Seagate/cortx-re && cd $PWD/cortx-re/solutions/kubernetes
```

-  To create the host file and change the root user password with your system password, run the following command.
```
# echo "hostname=$(hostname),user=root,pass=rootuserpassword" > hosts && cat hosts
```

-  Execute `cluster-setup.sh` to setup K8s cluster on your VM.  
-  If you want to run pods on to the primary node as well, pass the first input parameter for `cluster-setup.sh` script as true.  
-  If you have single node cluster, pass the input parameter as true.

```
# ./cluster-setup.sh true
```

## Deploy CORTX Stack 

Execute `cortx-deploy.sh` to deploy the CORTX on your K8s Cluster.

```
# export SOLUTION_CONFIG_TYPE=automated && ./cortx-deploy.sh --cortx-cluster
```

#### Note:  

Following parameter/s are passed when the cluster deployment command executes. If no parameter is passed, the default ones are chosen.

| Parameter     | Default value     | Description     |
| :------------- | :----------- | :---------|
| CORTX_SCRIPTS_BRANCH      | v0.5.0  | If you want to use another cortx-K8s branch then export this variable with your branch.     |
| CORTX_SCRIPTS_REPO | Seagate/cortx-k8s | If you want to use another cortx-K8s repo (like your fork), export this variable with your repo. |
| CORTX_SERVER_IMAGE | ghcr.io/seagate/cortx-rgw:2.0.0-latest | Also, if you want to use different server image then export this variable with new image. |
| CORTX_DATA_IMAGE | ghcr.io/seagate/cortx-data:2.0.0-latest | Also, if you want to use different data image then export this variable with new image. |
|CONTROL_EXTERNAL_NODEPORT | 31169 | If you want to use different port for control service, export this variable with another port. |
| S3_EXTERNAL_HTTP_NODEPORT | 30080 | If you want to use different port for HTTP Port to IO service, then export this variable with another port. |
| S3_EXTERNAL_HTTPS_NODEPORT | 30443 | If you want to use different port for HTTPS Port to IO service, then export this variable with another port. |
| SOLUTION_CONFIG_TYPE | manual | There are two config types for solution.yaml file; manual and automated. In automated type the solution.yaml is created by script if VM is created as per standard specification. In manual type the user needs to create solution.yaml with required disks, image details etc.; place it at script location and configure SOLUTION_CONFIG_TYPE variable as manual. |
| SNS_CONFIG | 1+0+0 | SNS configuration for deployment. Please select value based on disks available on nodes. |
| DIX_CONFIG | 1+0+0 | DIX configuration for deployment. Please select value based on disks available on nodes. |

For example:
```
# export CORTX_SCRIPTS_BRANCH=integration && export CORTX_SCRIPTS_REPO=AbhijitPatil1992/cortx-k8s && export SOLUTION_CONFIG_TYPE=automated && ./cortx-deploy.sh --cortx-cluster
```

Run IO Sanity on your CORTX Cluster to validate if you are able to create bucket and upload the object in deployed cluster.
```
# ./cortx-deploy.sh --io-sanity
```

Tested by:

* May 06, 2022: Rahul Shenoy (rahul.shenoy@seagate.com) - Windows , VMware Workstation 16 , CentOS 7.9 Linux