# Setup K8s Cluster and Deploy CORTX Stack

The following sections discusses how to set up the K8s cluster and deploy the CORTX Stack. The minimum prerequisites are enlisted to make sure the cluster set up process is smooth.

## Prerequisite 
Following are the prerequisites to set up the K8s cluster and deploy the CORTX stack

### Minimum system specifications required for Cortx Stack
   - RAM: 16GB
   - CPU: 8Core
   - DISK: Need 8 Disk with 25GB per disk

### Types of configurations available for `solution.yaml` file
-  Manual 
-  Automated: When using automated configuration, please make sure your VM(virtual machine) has following drives available on it:

```
ls /dev/sd*
```
`/dev/sdb  /dev/sdc  /dev/sdd  /dev/sde  /dev/sdf  /dev/sdg  /dev/sdh  /dev/sdi  /dev/sdj`

### You must have Git installed on your system.
You can use following command for RedHat family OS to install the Git

```
yum install git -y
```

### SELinux should be disabled

Use the following command to check status of SELinux.
```
sestatus
```
If SELinux is enable, run the following command to disable the SELinux.

```
sed -i 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config && setenforce 0
```

Once above command runs successfully, reboot your system.

```
reboot
```   

### Note
 1. All the nodes should be reachable over SSH with root user.
 2. You also deploy CORTX on AWS EC2 instance. Please follow [CORTX Deployment on AWS](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/cloud/AWS/README.md)

## Install K8s cluster
**To install the K8s cluster, run the following commands:**

Clone cortx-re repository and change directory to `cortx-re/solutions/kubernetes`
```
git clone https://github.com/Seagate/cortx-re 
cd $PWD/cortx-re/solutions/kubernetes
```

To create the host file and change the root user password, run the following command
```
echo "hostname=$(hostname),user=root,pass=rootuserpassword" > hosts && cat hosts
```

Execute `cluster-setup.sh` to setup K8s cluster on your VM.  
If you want to run pods on to the master node as well, give first input parameter for `cluster-setup.sh` script as true else false.  
If you have single node cluster, pass the input parameter as true.

```
./cluster-setup.sh true
```

## Deploy the Cortx Stack on K8s Cluster

Execute `cortx-deploy.sh` to deploy Cortx Cluster on your K8s Cluster.

```
export SOLUTION_CONFIG_TYPE=automated && ./cortx-deploy.sh --cortx-cluster
```

Note:
   1. CORTX_SCRIPTS_BRANCH : If you want to use another cortx-K8s branch then export this variable with your branch (Default value CORTX_SCRIPTS_BRANCH="v0.2.1").
   2. CORTX_SCRIPTS_REPO : If you want to use another cortx-K8s repo (like your fork), export this variable with your repo (Default value CORTX_SCRIPTS_REPO="Seagate/cortx-k8s").
   3. CORTX_ALL_IMAGE : In automated case we are using latest cortx-all image. If you want to use different image then export the image by this variable (Default value CORTX_ALL_IMAGE=ghcr.io/seagate/cortx-all:2.0.0-latest).
   4. CORTX_SERVER_IMAGE : Also, if you want to use different server image then export this variable with new image (Default value CORTX_SERVER_IMAGE=ghcr.io/seagate/cortx-rgw:2.0.0-latest).
   5. CORTX_DATA_IMAGE : Also, if you want to use different data image then export this variable with new image (Default value CORTX_DATA_IMAGE=ghcr.io/seagate/cortx-data:2.0.0-latest).
   6. CONTROL_EXTERNAL_NODEPORT : If you want to use different port for control service, export this variable with another port (Default value CONTROL_EXTERNAL_NODEPORT="31169").
   7. S3_EXTERNAL_HTTP_NODEPORT : If you want to use different port for HTTP Port to IO service, then export this variable with another port (Default value S3_EXTERNAL_HTTP_NODEPORT="30080").
   8. S3_EXTERNAL_HTTPS_NODEPORT : If you want to use different port for HTTPS Port to IO service, then export this variable with another port (Default value S3_EXTERNAL_HTTPS_NODEPORT="30443").
   9. SOLUTION_CONFIG_TYPE : There are two types config for solution.yaml file; manual and automated. 
   - Automated means solution.yaml is created automatically but it needs VM with required specifications.
   - Manual means we need to create solution.yaml file and replace it at script location. If you wanted to create solution yaml manually, then create it and place it at script location and configure SOLUTION_CONFIG_TYPE variable as manual (Default value SOLUTION_CONFIG_TYPE=manual).

For example:
```
export CORTX_SCRIPTS_BRANCH=integration && export CORTX_SCRIPTS_REPO=AbhijitPatil1992/cortx-k8s && export SOLUTION_CONFIG_TYPE=automated && ./cortx-deploy.sh --cortx-cluster
```

Run IO Sanity on your Cortx Cluster
```
./cortx-deploy.sh --io-sanity
```
