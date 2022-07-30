# Build & Deploy CORTX Stack on Amazon Web Services 

This document discusses the procedure to compile the CORTX Stack and deploy on AWS instances.

## Prerequisite

- Ensure that you have an AWS account with Secret Key and Access Key.
- Build and deploy CORTX Stack on all the AWS instances in the cluster.

## Install and setup Terraform and AWS CLI

- Clone the `cortx-re` repository and change the directory to `cortx-re/solutions/community-deploy/cloud/AWS`.
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

- Modify `user.tfvars` file with your AWS details.
```
vi user.tfvars
```
- #### Note:  

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

## Create AWS instance

- Execute Terraform code (as shown below) to create AWS instances for CORTX Build and Deployment.
```
terraform validate && terraform apply -var-file user.tfvars --auto-approve
```
- The following commands will display the public and private ip addresses so use these ip addresses to connect to AWS instances
  using SSH Protocol.
  
**Public ip addresses:**
```
terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_public_ip_addr.value 2>&1 | tee ip.txt  | tr -d '",[]' | sed '/^$/d'
```
**Private ip addresses:**
```
terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_private_ip_addr.value 2>&1 | tee ip.txt  | tr -d '",[]' | sed '/^$/d'
```

## Network and Storage Configuration.

- Execute `/home/centos/setup.sh` on primary node in the cluster to setup network and storage devices for CORTX.

#### Note:
`/home/centos/setup.sh` will reboot the nodes.
```
for instance in node{1..3};do 
   ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-primarynode>" sudo bash /home/centos/setup.sh
   ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-workernode1>" sudo bash /home/centos/setup.sh
   ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-workernode2>" sudo bash /home/centos/setup.sh
done
```

- AWS instances are ready for CORTX Build and deployment now. Connect to EC2 instances over SSH and validate that all three network cards has IP address assigned.
   
- Generate `root` user password on all the EC2 instances in the cluster.
*The root password is required as a part of CORTX deployment.*

```
sudo passwd root
```

## CORTX Build and Deployment

### CORTX Build

**Note:** Execute all instructions on primary node only.

- We will use [cortx-build](https://github.com/Seagate/cortx/pkgs/container/cortx-build) docker image to compile entire CORTX stack.  
- Login into AWS instance over SSH using public IP address,
```
ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-primarynode>"
```

- Clone cortx-re repository on all the nodes in the cluster and switch to `solutions/community-deploy` directory
```
git clone https://github.com/Seagate/cortx-re && cd $PWD/cortx-re/solutions/community-deploy
```

- Execute `build-cortx.sh` which will generate CORTX container images from `main` of CORTX components
```
sudo time ./build-cortx.sh
```
- Copy pem file from local node to all the AWS EC2 instances using public ip address,
```
SRC_PATH="/root/cortx-re/solutions/community-deploy/cloud/AWS"
DST_PATH="/home/centos/cortx-re/solutions/community-deploy"
for instance in node{1..3};do
  rsync -avzrP -e 'sudo ssh -i cortx.pem -o StrictHostKeyChecking=no' ${SRC_PATH}/cortx.pem  centos@"<AWS instance public-ip-primarynode>":${DST_PATH}
  rsync -avzrP -e 'sudo ssh -i cortx.pem -o StrictHostKeyChecking=no' ${SRC_PATH}/cortx.pem  centos@"<AWS instance public-ip-workernode1>":${DST_PATH}
  rsync -avzrP -e 'sudo ssh -i cortx.pem -o StrictHostKeyChecking=no' ${SRC_PATH}/cortx.pem  centos@"<AWS instance public-ip-workernode2>":${DST_PATH}
```

### CORTX Deployment

- After CORTX build is ready, follow [CORTX Deployment](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/CORTX-Deployment.md) to deploy CORTX on AWS instance.   
- Please exclude SELINUX and Hostname setup steps.

## Cleanup 

You can clean-up the AWS infrastructure created using following command,
```
terraform validate && terraform destroy -var-file user.tfvars --auto-approve
```

Tested by:

* July 28, 2022: Mukul Malhotra (mukul.malhotra@seagate.com) - AWS EC2, CentOS 7.9 Linux
* May 06, 2022: Rahul Shenoy (rahul.shenoy@seagate.com) - Windows + VMware Workstation 16 + CentOS 7.9 Linux
* April 29, 2022: Pranav Sahasrabudhe (pranav.p.sahasrabudhe@seagate.com) - Mac + VMware Fusion 12 + CentOS 7.9 Linux
