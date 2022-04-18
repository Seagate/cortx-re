# Setup K8s Cluster and Deploy CORTX Stack

   This file consists of the procedure to setup K8s cluster and deploy Cortx stack.

## 1. Prerequisite 
#### Minimum specification required for Cortx Stack
   RAM: 16GB
   CPU: 8Core
   DISK: 100GB

#### If you have automated deployment for solution yaml, please make sure your vm having below drives
```
      ls /dev/sd*
```
   - `/dev/sdb  /dev/sdc  /dev/sdd  /dev/sde  /dev/sdf  /dev/sdg  /dev/sdh  /dev/sdi  /dev/sdj`

#### If Git doesn't exist in your system then run below command to install it

```
      yum install git -y
```
#### SELinux should be disable
```
      sestatus
```
   If SELinux is enable then you can run below command to disabled SELinux.

```
      sed -i 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config && setenforce 0
```

   Once above command ran successfully then reboot your system.

```
      reboot
```   
#### Note: 
 1. All node should be ssh connection happen properly
 2. If you don't have VM and if you want to create EC2 Instance. Then follow this document [link](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/cloud/AWS/README.md)

## 3. Install K8s cluster and deploy cortx cluster on that K8s cluster

   Clone cortx-re repository and change directory to `cortx-re/solutions/kubernetes`
```
      git clone https://github.com/Seagate/cortx-re 
      cd $PWD/cortx-re/solutions/kubernetes
```
   Create hosts file by below command, change root user password in below command.
```
      echo "hostname=$(hostname),user=root,pass=rootuserpassword" > hosts && cat hosts
```
   Execute `cluster-setup.sh` to setup K8s cluster on your VM.
```
      ./cluster-setup.sh true
```
   Execute `cortx-deploy.sh` to deploy Cortx Cluster on your K8s Cluster.
```
      export SOLUTION_CONFIG_TYPE=automated && ./cortx-deploy.sh --cortx-cluster
```
   Run IO Sanity on your Cortx Cluster, use below command to run it.
```
      ./cortx-deploy.sh --io-sanity
```
