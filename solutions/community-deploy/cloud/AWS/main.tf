# Copyright (c) 2021 Seagate Technology LLC and/or its Affiliates
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# For any questions about this software or licensing,
# please email opensource@seagate.com or cortx-questions@seagate.com.
#

terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 3.27"
    }
  }
  required_version = ">= 0.15.1"
}

provider "aws" {
  profile = "default"
  region  = var.region
}

resource "aws_default_vpc" "default" {}

data "aws_availability_zones" "available" {
  state = "available"
}

resource "aws_default_subnet" "default" {
  availability_zone = data.aws_availability_zones.available.names[0]
}

locals {
  ec2_device_names_to_attach = slice(var.ec2_device_names, 0, var.ebs_volume_count)
  common_tags = {
    "Terraform" = "true",
    "CORTX"     = "true"
  }
}

resource "aws_security_group" "cortx_deploy" {
  name        = "${var.tag_name}"
  description = "Allow standard ssh, CORTX mangement ports inbound and everything else outbound."

  ingress {
    description = "SSH Acces"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.security_group_cidr]
  }

  ingress {
    description = "Allow access within security group"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.tag_name}"
  }
}

data "aws_ami" "centos" {
  most_recent = true
  owners      = ["125523088429"]

  filter {
    name   = "name"
    values = [var.os_version]
  }

  filter {
    name   = "architecture"
    values = ["x86_64"]
  }

  filter {
    name   = "root-device-type"
    values = ["ebs"]
  }
}

resource "tls_private_key" "example" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "cortx_key" {
  key_name   = var.key_name
  public_key = tls_private_key.example.public_key_openssh
}

resource "local_sensitive_file" "pem_file" {
  filename             = "${path.module}/cortx.pem"
  file_permission      = "600"
  directory_permission = "700"
  content    = tls_private_key.example.private_key_pem
}

resource "aws_instance" "cortx_deploy" {
  # https://wiki.centos.org/Cloud/AWS
  count                  = "${var.instance_count}"		
  ami                    = data.aws_ami.centos.id
  instance_type          = "t3a.2xlarge"
  availability_zone      = data.aws_availability_zones.available.names[0]
  key_name               = aws_key_pair.cortx_key.key_name
  vpc_security_group_ids = [aws_security_group.cortx_deploy.id]
  user_data              = <<-EOT
        #!/bin/bash
        sed -i '/PasswordAuthentication no/s/no/yes/g' /etc/ssh/sshd_config
        systemctl restart sshd
        sed -i 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config
        /sbin/setenforce 0
        yum install -y yum-utils git firewalld epel-release && yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo -y && yum install -y jq docker-ce docker-ce-cli containerd.io docker-compose-plugin
  EOT
  root_block_device {
    volume_size = 90
  }

  tags = {
    Name = "${var.tag_name}-${count.index + 1}"
  }

  provisioner "file" {
    source      = "setup.sh"
    destination = "setup.sh"

    connection {
      type        = "ssh"
      user        = "centos"
      private_key = tls_private_key.example.private_key_pem
      host        = self.public_dns
    }
  }
}

output "aws_instance_public_ip_addr" {
  value       = aws_instance.cortx_deploy.*.public_ip
  description = "Public IP to connect CORTX server"
  }

output "aws_instance_private_ip_addr" {
  value       = aws_instance.cortx_deploy.*.private_ip
  description = "Private IP to connect to EC2 Instances"
  }

resource "aws_ebs_volume" "data_vol" {
  count             = var.ebs_volume_count * var.instance_count
  availability_zone = data.aws_availability_zones.available.names[0]
  size              = var.ebs_volume_size
  tags = {
    Name = "${var.tag_name}-${count.index + 1}"
  }
}

resource "aws_volume_attachment" "deploy_server_data" {
  count       = var.ebs_volume_count * var.instance_count
  volume_id   = aws_ebs_volume.data_vol.*.id[count.index]
  device_name = element(local.ec2_device_names_to_attach, count.index)
  instance_id = element(aws_instance.cortx_deploy.*.id, floor(count.index/var.ebs_volume_count))
}
