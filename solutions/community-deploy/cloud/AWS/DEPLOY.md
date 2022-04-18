# Setup K8s Cluster and Deploy CORTX Stack

   This file consists of the procedure to setup K8s cluster and deploy Cortx stack.

## 1. Prerequisite 
#### Minimum specification require for Cortx Stack
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
#### Note: 1. All node should ssh connection happen properly
####       2. If you don't have VM and if you want to create EC2 Instance. Then follow this document [link](https://github.com/Seagate/cortx-re/tree/main/solutions/community-deploy/cloud/AWS)

## 3. Install K8s cluster and deploy cortx cluster on that K8s cluster

   Clone cortx-re repository and change directory to `cortx-re/solutions/kubernetes`
```
      git clone https://github.com/Seagate/cortx-re 
      cd $PWD/cortx-re/solutions/kubernetes
```
   Create hosts file by below command change root user password in below command.
```
      echo "hostname=$(hostname),user=root,pass=rootuserpassword" > hosts && cat hosts
```
   Execute `cluster-setup.sh` to setup K8s cluster on your VM. Once your K8s setup script run successfully then check your nodes should ready in state.
```
      ./cluster-setup.sh true
      kubectl get nodes
```
   Execute `cortx-deploy.sh` to deploy Cortx Cluster on your K8s cluster and run IO Sanity on your Cortx Cluster.
```
      export SOLUTION_CONFIG_TYPE=automated && ./cortx-deploy.sh --cortx-cluster
      ./cortx-deploy.sh --io-sanity
```

## 4. Cleanup only EC2 instance

   Once your work done with above instance then you can exit your instance and run clean-up script on it. You can use below command to clean-up all AWS infrastructure.
```
      terraform validate && terraform destroy -var-file user.tfvars --auto-approve
```
