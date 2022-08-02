# Build & Deploy CORTX Stack on Amazon Web Services 

This document discusses the procedure to compile the CORTX Stack and deploy on AWS instances.

**Prerequisites:**

- Ensure that you have an AWS account with Secret Key and Access Key.
- Build and deploy CORTX Stack on all the AWS instances in the cluster.
- Clone the `cortx-re` repository on all nodes and then change the directory to `cortx-re/solutions/community-deploy/cloud/AWS`.
```
git clone https://github.com/Seagate/cortx-re && cd $PWD/cortx-re/solutions/community-deploy/cloud/AWS
```
- Install the required tools.
```
./tool_setup.sh
```
 - During tools installation, your are prompted to enter the AWS Access and Secret key. For more details, refer [AWS CLI Configuration](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-quickstart.html#cli-configure-quickstart-config).
- Execute below command to check the connectivity with AWS
```
aws sts get-caller-identity
```

## Procedure

- Modify `user.tfvars` file on local host with your AWS details.
```
vi user.tfvars
```
**Note:**
Following parameter/s are passed when the cluster deployment command executes. If no parameter is passed, the default ones are chosen.

| Parameter     | Example     | Description     |
| :------------- | :----------- | :---------|
| os_version      | CentOS 7.9.2009 x86_64  | This will help you to select the ami of EC2 machine. |
| region | ap-south-1 | You can pick any region from this region code : https://awsregion.info/  |
| security_group_cidr | 134.204.222.36/32  | You need to find the own Public IP using this command : `curl ipinfo.io/ip`. Also calculate CIDR for IP using Subnet Calculator from https://mxtoolbox.com/subnetcalculator.aspx |
| key_name | devops-key | You can pass .pem key file name to login to aws EC2 instance in `key_name`. |

- Contents of `user.tfvars` file should display as follows:
```
cat user.tfvars
os_version          = "CentOS 7.9.2009 x86_64"
region              = "ap-south-1"
security_group_cidr = "134.204.222.36/32"
key_name            = "devops-key"
instance_count      = "3"
ebs_volume_count    = "9"
ebs_volume_size     = "10"
tag_name            = "cortx-multinode"
```

### Execute Instructions from Local Host to create AWS Instances and Network and Storage Configuration
- Execute Terraform code (as shown below) to create AWS instances for CORTX Build and Deployment.
```
terraform validate && terraform apply -var-file user.tfvars --auto-approve
```
- Execute following commands which will perform on all the nodes in the cluster,
  - Setup network and storage devices for CORTX.
  - Prompt for generating the `root` user password
  - `/home/centos/setup.sh` will reboot all the nodes once executed

**Note:**
*The root password is required as a part of CORTX deployment.*
```
PUBLIC_IP=`terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_public_ip_addr.value 2>&1 | tee ip.txt  | tr -d '",[]' | sed '/^$/d'`
for ip in $PUBLIC_IP;do
   ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$ip 'echo "Enter new password for root user" && sudo passwd root && sudo bash /home/centos/setup.sh'
   ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$ip 'echo "Enter new password for root user" && sudo passwd root && sudo bash /home/centos/setup.sh'
   ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$ip 'echo "Enter new password for root user" && sudo passwd root && sudo bash /home/centos/setup.sh'
done
```
- AWS instances are ready for CORTX Build and deployment now. Connect to EC2 nodes over SSH and validate that all three network cards has IP address assigned.

### CORTX Build

- We will use [cortx-build](https://github.com/Seagate/cortx/pkgs/container/cortx-build) docker image to compile entire CORTX stack.
- Execute `build-cortx.sh` from primary node using public ip address which will generate CORTX container images from `main` of CORTX components
```
sudo su && cd /home/centos/cortx-re/solutions/community-deploy
time build-cortx.sh
```
- Save and compress the cortx build images
**Note:** The process might take some time to save and compress the images.
```
cd /tmp && docker save -o cortx-rgw.tar cortx-rgw:2.0.0-0 && docker save -o cortx-all.tar cortx-all:2.0.0-0 && \
docker save -o cortx-data.tar cortx-data:2.0.0-0 && docker save -o cortx-control.tar cortx-control:2.0.0-0 && \
docker save -o nginx.tar nginx:latest && docker save -o cortx-build.tar ghcr.io/seagate/cortx-build:rockylinux-8.4
```
- Execute the following command to copy the cortx build images from primary node to worker nodes using private ip address
```
rsync -avzrP -e 'sudo ssh -i cortx.pem -o StrictHostKeyChecking=no' /tmp/*.tar  centos@"<AWS instance private-ip-workernode1>":/tmp
rsync -avzrP -e 'sudo ssh -i cortx.pem -o StrictHostKeyChecking=no' /tmp/*.tar  centos@"<AWS instance private-ip-workernode2>":/tmp
```

**Note:** You can find the private ip address by executing the following command

**From Local Host:**
```
terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_private_ip_addr.value 2>&1 | tee ip.txt  | tr -d '",[]' | sed '/^$/d
```
**From EC2 node:**
```
curl http://169.254.169.254/latest/meta-data/local-ipv4
```

### Execute Instructions from Worker nodes
- Login to all worker nodes and load the cortx build images
```
for image in /tmp/*.tar; do cat $image | docker load; done
```

### CORTX Deployment

- After CORTX build is ready, follow [CORTX Deployment](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/CORTX-Deployment.md) to deploy CORTX on AWS instance.   
- Please exclude SELINUX and Hostname setup steps.

### Cleanup 

You can clean-up the AWS infrastructure created using following command,
```
terraform validate && terraform destroy -var-file user.tfvars --auto-approve
```

Tested by:

* July 30, 2022: Mukul Malhotra (mukul.malhotra@seagate.com) - AWS EC2, CentOS 7.9 Linux
* May 06, 2022: Rahul Shenoy (rahul.shenoy@seagate.com) - Windows + VMware Workstation 16 + CentOS 7.9 Linux
* April 29, 2022: Pranav Sahasrabudhe (pranav.p.sahasrabudhe@seagate.com) - Mac + VMware Fusion 12 + CentOS 7.9 Linux
