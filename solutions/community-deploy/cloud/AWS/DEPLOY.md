# Setup K8s Cluster and Deploy CORTX Stack

   Please find below steps to setup K8s cluster and deploy CORTX Stack.

## Prerequisite 
### Minimum specification required for Cortx Stack
   - RAM: 16GB
   - CPU: 8Core
   - DISK: Need 8 Disk with 10GB per disk.

### If you have automated deployment for solution yaml, please make sure your vm having below drives available
```
      ls /dev/sd*
```
   - `/dev/sdb  /dev/sdc  /dev/sdd  /dev/sde  /dev/sdf  /dev/sdg  /dev/sdh  /dev/sdi  /dev/sdj`

### If Git doesn't exist in your system then run below command to install it

```
      yum install git -y
```
### SELinux should be disable
   - Use below command to check status of SELinux.
```
      sestatus
```
   - If SELinux is enable then you can run below command to disabled SELinux.

```
      sed -i 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config && setenforce 0
```

   - Once above command ran successfully then reboot your system.

```
      reboot
```   
### Note: 
 1. All node should be ssh connection happen properly
 2. If you wanted to deploy on AWS environment then follow this document [link](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/cloud/AWS/README.md)

## Install K8s cluster and deploy cortx cluster on that K8s cluster

   - Clone cortx-re repository and change directory to `cortx-re/solutions/kubernetes`
```
      git clone https://github.com/Seagate/cortx-re 
      cd $PWD/cortx-re/solutions/kubernetes
```
   - Create hosts file by below command, change root user password in below command.
```
      echo "hostname=$(hostname),user=root,pass=rootuserpassword" > hosts && cat hosts
```
   - Execute `cluster-setup.sh` to setup K8s cluster on your VM.
```
      ./cluster-setup.sh true
```
   - Execute `cortx-deploy.sh` to deploy Cortx Cluster on your K8s Cluster. In this document we are creating `solution.yaml` file automatically. If you wanted to create it manually, then create it and place it at script location and configure SOLUTION_CONFIG_TYPE variable as manual (export SOLUTION_CONFIG_TYPE=manual).
```
      export SOLUTION_CONFIG_TYPE=automated && ./cortx-deploy.sh --cortx-cluster
```

   Note:
   1. CORTX_SCRIPTS_BRANCH - If you want to use another cortx-K8s branch then export this variable with your branch.
   2. CORTX_SCRIPTS_REPO - If you want to use another cortx-K8s repo (like your fork), then export this variable with your repo.
   3. CORTX_ALL_IMAGE - In automated case we are using latest cortx-all image. If you want to use different image then export that image by this variable.
   4. CORTX_SERVER_IMAGE - Also cortx-server image if you want to use different then export this variable with that image.
   5. CORTX_DATA_IMAGE - Also cortx-data image if you want to use different then export this variable with that image.
   6. CONTROL_EXTERNAL_NODEPORT - If you want to use different port for control service, then export this variable with another port.
   7. S3_EXTERNAL_HTTP_NODEPORT - If you want to use different port for HTTP Port to IO service, then export this variable with another port.
   8. S3_EXTERNAL_HTTPS_NODEPORT - If you want to use different port for HTTPS Port to IO service, then export this variable with another port.

      e.g.
```
      export CORTX_SCRIPTS_BRANCH=integration && export CORTX_SCRIPTS_REPO=AbhijitPatil1992/cortx-k8s && export SOLUTION_CONFIG_TYPE=automated && ./cortx-deploy.sh --cortx-cluster
```

   - Run IO Sanity on your Cortx Cluster, use below command to run it.
```
      ./cortx-deploy.sh --io-sanity
```
