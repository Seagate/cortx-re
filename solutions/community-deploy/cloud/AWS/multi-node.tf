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
  common_tags = {
    "Terraform" = "true",
    "CORTX"     = "true"
  }
}

resource "aws_security_group" "cortx_deploy" {
  name        = "cortx_deploy"
  description = "Allow standard ssh, CORTX mangement ports inbound and everything else outbound."

  ingress {
    description = "SSH Acces"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.security_group_cidr]
  }

  ingress {
    description = "CORTX UI Access"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = [var.security_group_cidr]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = local.common_tags
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


}
resource "aws_instance" "cortx_deploy" {
  # https://wiki.centos.org/Cloud/AWS
  count                  = var.instance_count
  ami                    = data.aws_ami.centos.id
  instance_type          = "t3a.2xlarge"
  availability_zone      = data.aws_availability_zones.available.names[0]
  vpc_security_group_ids = [aws_security_group.cortx_deploy.id]
  user_data              = <<-EOT
        #!/bin/bash
        sed -i '/PasswordAuthentication no/s/no/yes/g' /etc/ssh/sshd_config
        systemctl restart sshd
        sed -i 's/SELINUX=enforcing/SELINUX=disabled/' /etc/selinux/config
        /sbin/setenforce 0
        yum install -y yum-utils git firewalld -y
  EOT
  root_block_device {
    volume_size = 90
  }


  tags = {
    Name = "deployment-poc-${count.index +1}"
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
resource "aws_ebs_volume" "data_vol" {
  count             = 9
  availability_zone = data.aws_availability_zones.available.names[0]
  size              = 10

  tags = local.common_tags
}

variable "ec2_device_names" {
  default = [
    "/dev/sdb",
    "/dev/sdc",
    "/dev/sdd",
    "/dev/sde",
    "/dev/sdf",
    "/dev/sdg",
    "/dev/sdh",
    "/dev/sdi",
    "/dev/sdj",
  ]
}

resource "aws_volume_attachment" "deploy_server_data" {
  count       = 9
  volume_id   = aws_ebs_volume.data_vol.*.id[count.index]
  device_name = element(var.ec2_device_names, count.index)
  instance_id = element(aws_instance.cortx_deploy.*.id, count.index)
}