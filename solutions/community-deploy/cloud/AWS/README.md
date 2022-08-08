# Build & Deploy CORTX Stack on Amazon Web Services 

This document discusses the procedure to compile the CORTX Stack and deploy on AWS instances.

**Prerequisites:**

- Ensure that you have an AWS account with Secret Key and Access Key.
- Clone the `cortx-re` repository and then change the directory to `cortx-re/solutions/community-deploy/cloud/AWS`.
```
git clone https://github.com/Seagate/cortx-re && cd $PWD/cortx-re/solutions/community-deploy/cloud/AWS
```
- Install the required tools on the local host
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

### Execute Instructions from Local Host to create AWS Instances and Network and Storage Configuration
- Execute the commands from `$PWD/cortx-re/solutions/community-deploy/cloud/AWS` directory
- Execute Terraform code (as shown below) to create AWS instances for CORTX Build and Deployment.
```
terraform validate && terraform apply -var-file user.tfvars --auto-approve
```
- Execute the following commands to find the Public and Private ip addresses from local host by setting as environment variable.

**Public ip address:**
```
export PUBLIC_IP=`terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_public_ip_addr.value 2>&1 | tee ip_public.txt  | tr -d '",[]' | sed '/^$/d'`
```
**Private ip address:**
```
export PRIVATE_IP=`terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_private_ip_addr.value 2>&1 | tee ip_private.txt  | tr -d '",[]' | sed '/^$/d'`
```
- Execute the following commands on all the nodes which will perform the following actions:
  - Setup network and storage devices for CORTX.
  - Generating the `root` user password which is required as a part of CORTX deployment
  - `setup.sh` will reboot all the nodes once executed
  - Copy the pem file from primary node to the worker nodes using private ip address

```
for ip in $PUBLIC_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$ip 'echo "Enter new password for root user" && sudo passwd root && sudo bash /home/centos/setup.sh'; done
```
```
for ip in $PUBLIC_IP;do rsync -avzrP -e 'sudo ssh -i cortx.pem -o StrictHostKeyChecking=no' cortx.pem centos@$ip:/tmp; done
```
- AWS instances are ready for CORTX Build and deployment now. Connect to EC2 nodes over SSH and validate that all three network cards has IP address assigned.

### CORTX Build
- We will use [cortx-build](https://github.com/Seagate/cortx/pkgs/container/cortx-build) docker image to compile entire CORTX stack.
- Execute `build-cortx.sh` from primary node using public ip address which will generate CORTX container images from `main` of CORTX components

**Note:** Become the **root** user after logged in to the primary node by running `sudo su` command.
```
ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@"<AWS instance public-ip-primarynode>" 'git clone https://github.com/Seagate/cortx-re'
sudo su && cd $PWD/cortx-re/solutions/community-deploy
time bash -x ./build-cortx.sh
```
- Execute the following command to copy the cortx build images from primary node to worker nodes using private ip address,
**For example:**
```
cd /tmp && rsync -avzrP -e 'sudo ssh -i cortx.pem -o StrictHostKeyChecking=no' /tmp/*.tar  centos@"<AWS instance private-ip-workernodes>":/tmp
```
**Note:** You can find the private ip address as referenced above from the local host or execute following command from EC2 instances,
```
curl http://169.254.169.254/latest/meta-data/local-ipv4
```
 
### Execute Instructions from Worker nodes
- Login to primary node and load the cortx build images
```
for image in /tmp/*.tar; do cat $image | docker load; done
```
- Clone cortx-re repository from required branch/tag. If you do not provide `-b <branch/tag>`, then it will use default main branch    
  :warning: Tag based build is supported after and including tag [2.0.0-879](https://github.com/Seagate/cortx-re/releases/tag/2.0.0-879) 
```
git clone https://github.com/Seagate/cortx-re -b <branch/tag>
```
- Switch to solutions/community-deploy directory 
```
cd $PWD/cortx-re/solutions/community-deploy
```  
- Generate CORTX container images from required branch/tag. If you do not provide `-b <branch/tag>`, then it will use default main branch  
  :warning: Tag based build is supported after and including tag [2.0.0-879](https://github.com/Seagate/cortx-re/releases/tag/2.0.0-879)
```
time ./build-cortx.sh -b <branch/tag>
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
