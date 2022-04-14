# Setup K8s Cluster and Deploy CORTX Stack on Amazon Web Services.

   This file consists of the procedure to setup K8s cluster and deploy Cortx stack.

### 1. Create EC2 instance

   If you don't have VM then follow [AWS EC2 Instance Create Terraform Script](https://github.com/Seagate/cortx-re/tree/main/solutions/community-deploy/cloud/AWS). If you already create VM then skip this step.


   Connect to system using SSH key or password and centos or your user. After that go with sudo privileges.

### 2. Prerequisite 
#### Please make your vm having below drives should be available.
```
      ls /dev/sd*
```
   - `/dev/sdb  /dev/sdc  /dev/sdd  /dev/sde  /dev/sdf  /dev/sdg  /dev/sdh  /dev/sdi  /dev/sdj`
#### SELinux should be disable
```
      sestatus
```
   If SELinux is enable then you can run below command to disabled SELinux. Once below command run then reboot your system.

```
      sed -i 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config
      setenforce 0
      reboot
```
### 3. Install K8s cluster and deploy cortx cluster on that K8s cluster.

   Clone cortx-re repository and change directory to `cortx-re/solutions/kubernetes`
```
      git clone https://github.com/Seagate/cortx-re 
      cd $PWD/cortx-re/solutions/kubernetes
```
   Create hosts file by below command change root user password in below command.
```
      echo "hostname=$(hostname),user=root,pass=rootuserpassword" > hosts && cat hosts
```
   Run git command if git doesn't exist in your system then run below command to install it.

```
      yum install git -y
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

### 4. Cleanup only EC2 instance

   Once your work don't with above instance then you can exit your VM and run clean-up script to clean your AWS infrastructure which you have created. You can clean-up all AWS infrastructure created using below command. 
```
      terraform validate && terraform destroy -var-file user.tfvars --auto-approve
```
