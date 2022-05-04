# Setup K8s Cluster and Deploy CORTX Stack

The following sections discusses how to set up the K8s cluster and deploy the CORTX Stack. The minimum prerequisites are enlisted to make sure the cluster set up process is smooth.

## Prerequisites
Following are the minimum system specifications required to set up the K8s cluster and deploy the CORTX stack

   - RAM: 16GB
   - CPU: 8Core
   - DISK: Need 8 Disk with 25GB per disk

## Deploy the CORTX Stack on K8s cluster
This section enlists the commands to deploy the CORTX Stack on K8s cluster. 

### Types of configurations available for `solution.yaml` file
-  Manual 
-  Automated: When using automated configuration, please make sure your VM(virtual machine) has following drives available on it:

```
# ls /dev/sd*

/dev/sda  /dev/sda1  /dev/sda2  /dev/sdb  /dev/sdc  /dev/sdd  /dev/sde  /dev/sdf  /dev/sdg  /dev/sdh  /dev/sdi
```

### You must have Git installed on your system.
You can use following command for RedHat family OS to install the Git

```
# yum install git -y
```

### SELinux should be disabled

Use the following command to check status of SELinux.
```
# sestatus
```
If SELinux is enable, run the following command to disable the SELinux.

```
# sed -i 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config && setenforce 0
```

Once above command runs successfully, reboot your system.

```
# reboot
```   

### Note
 1. All the nodes should be reachable over SSH with root user.
 2. You can deploy CORTX on AWS EC2 instance also. Please follow [CORTX Deployment on AWS](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/cloud/AWS/README.md)

## Install K8s cluster
**To install the K8s cluster, run the following commands:**

Clone cortx-re repository and change directory to `cortx-re/solutions/kubernetes`
```
# git clone https://github.com/Seagate/cortx-re && cd $PWD/cortx-re/solutions/kubernetes
```

To create the host file and change the root user password with your system password, run the following command
```
# echo "hostname=$(hostname),user=root,pass=rootuserpassword" > hosts && cat hosts
```

Execute `cluster-setup.sh` to setup K8s cluster on your VM.  
If you want to run pods on to the master node as well, pass the first input parameter for `cluster-setup.sh` script as true.  
If you have single node cluster, pass the input parameter as true.

```
# ./cluster-setup.sh true
```

## Deploy the CORTX Stack on K8s Cluster

Execute `cortx-deploy.sh` to deploy the CORTX on your K8s Cluster.

```
# export SOLUTION_CONFIG_TYPE=automated && ./cortx-deploy.sh --cortx-cluster
```

Note: 
Following parameter/s are passed when the cluster deployment command executes. If no parameter is passed, the default ones are chosen.
( Format used here : parameter name | default parameter value : description )

   1. CORTX_SCRIPTS_BRANCH | CORTX_SCRIPTS_BRANCH="v0.2.1" : If you want to use another cortx-K8s branch then export this variable with your branch.
   2. CORTX_SCRIPTS_REPO | CORTX_SCRIPTS_REPO="Seagate/cortx-k8s" : If you want to use another cortx-K8s repo (like your fork), export this variable with your repo.
   3. CORTX_ALL_IMAGE | CORTX_ALL_IMAGE=ghcr.io/seagate/cortx-all:2.0.0-latest :  In automated case we are using latest cortx-all image. If you want to use different image then export the image by this variable.
   4. CORTX_SERVER_IMAGE | CORTX_SERVER_IMAGE=ghcr.io/seagate/cortx-rgw:2.0.0-latest : Also, if you want to use different server image then export this variable with new image.
   5. CORTX_DATA_IMAGE | CORTX_DATA_IMAGE=ghcr.io/seagate/cortx-data:2.0.0-latest : Also, if you want to use different data image then export this variable with new image.
   6. CONTROL_EXTERNAL_NODEPORT | CONTROL_EXTERNAL_NODEPORT="31169" : If you want to use different port for control service, export this variable with another port.
   7. S3_EXTERNAL_HTTP_NODEPORT | S3_EXTERNAL_HTTP_NODEPORT="30080" : If you want to use different port for HTTP Port to IO service, then export this variable with another port.
   8. S3_EXTERNAL_HTTPS_NODEPORT | S3_EXTERNAL_HTTPS_NODEPORT="30443" : If you want to use different port for HTTPS Port to IO service, then export this variable with another port.
   9. SOLUTION_CONFIG_TYPE | SOLUTION_CONFIG_TYPE=manual : There are two types config for solution.yaml file; manual and automated. 
   -  Automated - solution.yaml is created by script if VM is created as per standard specification.
   -  Manual - User needs to create solution.yaml with required disks, image details etc.; place it at script location and configure SOLUTION_CONFIG_TYPE variable as manual.

For example:
```
# export CORTX_SCRIPTS_BRANCH=integration && export CORTX_SCRIPTS_REPO=AbhijitPatil1992/cortx-k8s && export SOLUTION_CONFIG_TYPE=automated && ./cortx-deploy.sh --cortx-cluster
```

Run IO Sanity on your Cortx Cluster to validate if you are able to create bucket and upload the object in deployed cluster
```
# ./cortx-deploy.sh --io-sanity
```
