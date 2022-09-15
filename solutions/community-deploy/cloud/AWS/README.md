# Build & Deploy CORTX Stack on Amazon Web Services 

This document discusses the procedure to compile the CORTX Stack and deploy on AWS environment (One/Multi-node)

**Prerequisites:**
During tools installation, you will be prompted to enter your AWS Access and Secret key so ensure that you have an AWS account with Secret Key and Access Key. For more details, refer [AWS CLI Configuration](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-quickstart.html#cli-configure-quickstart-config).

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
- Modify user.tfvars file on local host with your AWS details.
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
| instance_count | 4  | You can select the number of EC2 instances |
| tag_name | cortx-multinode | You can assign your tag name to the EC2 Instances |

- Contents of `user.tfvars` file should display as follows:

```
cat user.tfvars
os_version          = "CentOS 7.9.2009 x86_64"
region              = "ap-south-1"
security_group_cidr = "134.204.222.36/32"
key_name            = "devops-key"
instance_count      = "4"
ebs_volume_count    = "9"
ebs_volume_size     = "10"
tag_name            = "cortx-multinode"
```

### Create AWS instance
- Execute terraform code (as shown below) to create AWS instances for CORTX Build and Deployment
- The command will display public-ip and private hostname on completion or use below environment variables. Use this public-ip to connect AWS instance using SSH Protocol
```
terraform init
terraform validate && terraform apply -var-file user.tfvars --auto-approve
```
- Execute the following command to store environment variables for AWS instances which can used for public ipaddress and private hostname i.e. PUBLIC_IP and PRIVATE_HOSTNAME respectively
```
export PUBLIC_IP=$(terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_public_ip_addr.value 2>&1 | tee ip_public.txt | tr -d '",[]' | sed '/^$/d')
export PRIVATE_HOSTNAME=$(terraform show -json terraform.tfstate | jq .values.outputs.aws_instance_private_dns.value 2>&1 | tee ec2_hostname.txt | tr -d '",[]' | sed '/^$/d')
export PRIMARY_PUBLIC_IP=$(cat ip_public.txt | jq '.[0]'| tr -d '",[]')
export WORKER_IP=$(cat ip_public.txt | jq '.[0]','.[1]','.[2]','.[3]' | tr -d '",[]')
export HOST1=$(cat ec2_hostname.txt | jq '.[0]'| tr -d '",[]')
export CORTX_SERVER_IMAGE="${HOST1}:8080/seagate/cortx-rgw:2.0.0-0"
export CORTX_DATA_IMAGE="${HOST1}:8080/seagate/cortx-data:2.0.0-0"
export CORTX_CONTROL_IMAGE="${HOST1}:8080/seagate/cortx-control:2.0.0-0"
```

## Network and Storage Configuration
- Execute the following commands on all the nodes which will perform the following actions:
  - Setup network and storage devices for CORTX.
  - Generating the `root` user password which is required as a part of CORTX deployment
  - `setup.sh` will reboot all the nodes once executed
```
for ip in $PUBLIC_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$ip 'echo "Enter new password for root user" && sudo passwd root && sudo bash /home/centos/setup.sh'; done
```
- AWS instances are ready for CORTX Build and deployment now. Connect to EC2 nodes over SSH and validate that all three network cards has IP address assigned.

### CORTX Build
- We will use [cortx-build](https://github.com/Seagate/cortx/pkgs/container/cortx-build) docker image to compile entire CORTX stack
- Execute `build-cortx.sh` on primary node using public ip address which will generate CORTX container images from `main` of CORTX components
```
ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$PRIMARY_PUBLIC_IP "git clone https://github.com/Seagate/cortx-re && cd $PWD/cortx-re/solutions/community-deploy && time bash -x ./build-cortx.sh"
```
- Clone cortx-re repository from required branch/tag. If you do not provide `-b <branch/tag>`, then it will use default main branch    
  :warning: Tag based build is supported after including tag [2.0.0-879](https://github.com/Seagate/cortx-re/releases/tag/2.0.0-879)
  
**Note:** If you had cloned cortx-re repo earlier based on above instructions then remove it before following with `branch/tag` and to use the latest release from https://github.com/Seagate/cortx/releases

```
git clone https://github.com/Seagate/cortx-re -b <branch/tag> && cd $PWD/cortx-re/solutions/community-deploy
```

### CORTX Deployment
- After CORTX build is ready, follow [CORTX Deployment](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/CORTX-Deployment.md) to deploy CORTX on any deployment platform
- Follow below command to modify the `hosts` file for all the nodes on your AWS EC2 instances

**Note:**
You should provide your root password which was changed in earlier steps and switch to `$PWD/cortx-re/solutions/community-deploy/cloud/AWS`
directory to execute the following command
- In case of AWS exclude SELINUX and Hostname setup steps and execute below steps

```
export ROOT_PASSWORD=<YOUR_ROOT_PASSWORD>
for ip in $PUBLIC_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$ip 'pushd /home/centos/cortx-re/solutions/kubernetes && touch hosts && echo hostname="${HOSTNAME}",user=root,pass='${ROOT_PASSWORD}' > hosts && cat hosts';done
```
- Make sure `hosts` file entries include all the AWS Instances added, If they are not same then add the node entries according to multi-node
- Execute following command to modify `/etc/docker/daemon.json` on all the nodes and then restart docker deamon to generate CORTX images locally
```
for ip in $PUBLIC_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@ip "sudo -- sh -c 'pushd /home/centos/cortx-re/solutions/kubernetes && sed -i 's,cortx-docker.colo.seagate.com,${HOST1}:8080,g' /etc/docker/daemon.json && systemctl restart docker && sleep 120'";done
```
- Execute following command to pull images build locally on worker nodes
```
for wp in $WORKER_IP;do ssh -i cortx.pem -o 'StrictHostKeyChecking=no' centos@$wp "sudo docker pull '${CORTX_SERVER_IMAGE}' && sudo docker pull '${CORTX_DATA_IMAGE}' && sudo docker pull '${CORTX_CONTROL_IMAGE}'";done
```

### Cleanup
- You can clean-up the AWS infrastructure created using following command
```
terraform validate && terraform destroy -var-file user.tfvars --auto-approve
```

Tested by:

* July 30, 2022: Mukul Malhotra (mukul.malhotra@seagate.com) - AWS EC2, CentOS 7.9 Linux
* May 06, 2022: Rahul Shenoy (rahul.shenoy@seagate.com) - Windows + VMware Workstation 16 + CentOS 7.9 Linux
* April 29, 2022: Pranav Sahasrabudhe (pranav.p.sahasrabudhe@seagate.com) - Mac + VMware Fusion 12 + CentOS 7.9 Linux
