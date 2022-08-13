# Build & Deploy CORTX Stack on Amazon Web Services 

This document discusses the procedure to compile the CORTX Stack and deploy on AWS environment (One/Multi-node)

**Prerequisites:**

 - During tools installation, you will be prompted to enter your AWS Access and Secret key so ensure that you have an AWS account with Secret Key and Access Key. For more details, refer [AWS CLI Configuration](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-quickstart.html#cli-configure-quickstart-config).

## Procedure
- Clone the `cortx-re` repository and then change the directory to `cortx-re/solutions/community-deploy/cloud/AWS`
```
git clone https://github.com/Seagate/cortx-re && cd $PWD/cortx-re/solutions/community-deploy/cloud/AWS
```
- Install the required tools by executing the following script,
```
./tool_setup.sh
```
- Execute below command to check the connectivity with AWS
```
aws sts get-caller-identity
```
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
| ebs_volume_count | 9 |  You can select the number of EBS volumes |
| ebs_volume_size | 10 |  You can select the EBS volume size |
| instance_count | 3  | You can select the number of EC2 instances |
| tag_name | cortx-multinode | You can assign your tag name to the EC2 Instances |

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

### Execute Instructions to create AWS Instances and Network & Storage Configuration
- Execute Terraform code (as shown below) to create AWS instances for CORTX Build and Deployment.
```
terraform validate && terraform apply -var-file user.tfvars --auto-approve
```
- Execute the following commands to find the Public and Private ip addresses from local host by setting as environment variable.

**Public ip address:**
```
PUBLIC_IP=`terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_public_ip_addr.value 2>&1 | tee ip_public.txt | tr -d '",[]' | sed '/^$/d'`
```
**Private ip address:**
```
PRIVATE_IP=`terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_private_ip_addr.value 2>&1 | tee ip_private.txt | tr -d '",[]' | sed '/^$/d'`
```
- Execute the following commands on all the nodes which will perform the following actions:
  - Setup network and storage devices for CORTX.
  - Generating the `root` user password which is required as a part of CORTX deployment
  - `setup.sh` will reboot all the nodes once executed
```
for ip in $PUBLIC_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$ip 'echo "Enter new password for root user" && sudo passwd root && sudo bash /home/centos/setup.sh'; done
```
- Execute the following commands on all the nodes which will copy the pem file from primary node to the worker nodes using private ip address
```
for ip in $PUBLIC_IP;do rsync -avzrP -e 'sudo ssh -i cortx.pem -o StrictHostKeyChecking=no' cortx.pem ip_public.txt ip_private.txt centos@$ip:/tmp; done
```
- AWS instances are ready for CORTX Build and deployment now. Connect to EC2 nodes over SSH and validate that all three network cards has IP address assigned.

### CORTX Build
- We will use [cortx-build](https://github.com/Seagate/cortx/pkgs/container/cortx-build) docker image to compile entire CORTX stack.
- Execute `build-cortx.sh` from primary node using public ip address which will generate CORTX container images from `main` of CORTX components

**Note:** Become the **root** user after logged in to the primary node by running `sudo su` command.
```
AWS_PRIMARY_IP=$(cat ip_public.txt | jq '.[0]'| tr -d '",[]')
ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$AWS_PRIMARY_IP
git clone https://github.com/Seagate/cortx-re && cd $PWD/cortx-re/solutions/community-deploy
time bash -x ./build-cortx.sh
```
- Save and compress the cortx build images by running following command,
```
echo "Wait till the operation is completed..." && docker save $(docker images | sed '1d' | awk '{print $1 ":" $2 }') -o /tmp/cortximages.tar
```
- Execute the following command to copy the cortx build images from **primary node to all the worker nodes using private ip address**,
```
AWS_WORKER_IP=$(cat /tmp/ip_private.txt | jq '.[1]','.[2]' | tr -d '",[]')
for worker in $AWS_WORKER_IP;do cd /tmp && rsync -avzrP -e 'sudo ssh -i cortx.pem -o StrictHostKeyChecking=no' /tmp/*.tar centos@$worker:/tmp; done
```
- Load the cortx build images to the worker nodes by executing the following command,
```
for worker in $AWS_WORKER_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$worker 'sudo docker load -i /tmp/cortximages.tar'; done
```
- Clone cortx-re repository from required branch/tag. If you do not provide `-b <branch/tag>`, then it will use default main branch    
  :warning: Tag based build is supported after including tag [2.0.0-879](https://github.com/Seagate/cortx-re/releases/tag/2.0.0-879)
  
**Note:** If you had cloned cortx-re repo earlier based on above instructions then remove it before following with `branch/tag`
```
git clone https://github.com/Seagate/cortx-re -b <branch/tag> && cd $PWD/cortx-re/solutions/community-deploy
```

**For example:**
```
git clone https://github.com/Seagate/cortx-re -b 2.0.0-879 && cd $PWD/cortx-re/solutions/community-deploy
```
- Generate CORTX container images from required branch/tag. If you do not provide `-b <branch/tag>`, then it will use default main branch  
  :warning: Tag based build is supported after and including tag [2.0.0-879](https://github.com/Seagate/cortx-re/releases/tag/2.0.0-879)
```
sudo time bash -x ./build-cortx.sh -b <branch/tag>
```

### CORTX Deployment
- After CORTX build is ready, follow [CORTX Deployment](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/CORTX-Deployment.md) to deploy CORTX on AWS instance.   
- Please exclude SELINUX and Hostname setup steps.

### Cleanup
- You can clean-up the AWS infrastructure created using following command,
```
terraform validate && terraform destroy -var-file user.tfvars --auto-approve
```

Tested by:

* July 30, 2022: Mukul Malhotra (mukul.malhotra@seagate.com) - AWS EC2, CentOS 7.9 Linux
