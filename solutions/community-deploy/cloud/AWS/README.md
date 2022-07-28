# Build & Deploy CORTX Stack on Amazon Web Services 

This document discusses the procedure to compile the CORTX Stack and deploy it on AWS instance.


## Prerequisite 

Ensure that you have an AWS account with Secret Key and Access Key.

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

```

## Create AWS instance

- Execute Terraform code (as shown below) to create AWS instance for CORTX Build and Deployment.  
- The command will display public-ip on completion. Use this public-ip to connect AWS instance using SSH Protocol. 
```
terraform validate && terraform apply -var-file user.tfvars --auto-approve
```

## Network and Storage Configuration.

- Execute `/home/centos/setup.sh` on N-node to setup Network and Storage devices for CORTX. Script will reboot instances on completion. 

```
ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-node1>" sudo bash /home/centos/setup.sh
ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-node2>" sudo bash /home/centos/setup.sh
ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-node3>" sudo bash /home/centos/setup.sh
```

- AWS instance is ready for CORTX Build and deployment now. Connect to instance over SSH and validate that all three network cards has IP address assigned.
   
- Generate `root` user password.  
*The root password is required as a part of CORTX deployment.*
   
```
sudo su -
passwd root
```   

## CORTX Build and Deployment

### CORTX Build

- We will use [cortx-build](https://github.com/Seagate/cortx/pkgs/container/cortx-build) docker image to compile entire CORTX stack.  
- Login into AWS instances over SSH using public IP address from Terraform script execution output
```
ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-node1>"
ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-node2>"
ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-node3>"
```
- Clone cortx-re repository and switch to `solutions/kubernetes` directory
```
git clone https://github.com/Seagate/cortx-re && cd $PWD/cortx-re/solutions/community-deploy
```
- Execute `build-cortx.sh` on N-nodes. This script will generate CORTX container images from `main` of CORTX components
```
for instance in node{1..3}; do
   ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-node1>" sudo bash time build-cortx.sh
   ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-node2>" sudo bash time build-cortx.sh
   ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-node3>" sudo bash time build-cortx.sh
done
```

### CORTX Deployment

- After CORTX build is ready, follow [CORTX Deployment](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/CORTX-Deployment.md) to deploy CORTX on AWS instance.   
- Please exclude SELINUX and Hostname setup steps.

## Cleanup 

You can clean-up all AWS infrastructure created using following command. 
```
terraform validate && terraform destroy -var-file user.tfvars --auto-approve
```

Tested by:

* May 06, 2022: Rahul Shenoy (rahul.shenoy@seagate.com) - Windows + VMware Workstation 16 + CentOS 7.9 Linux
* April 29, 2022: Pranav Sahasrabudhe (pranav.p.sahasrabudhe@seagate.com) - Mac + VMware Fusion 12 + CentOS 7.9 Linux
