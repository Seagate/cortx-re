output "aws_instance_public_ip_addr" {
  value       = aws_instance.cortx_deploy.*.public_ip
  description = "Public IP to connect CORTX server"
  }

output "aws_instance_private_ip_addr" {
  value       = aws_instance.cortx_deploy.*.private_ip
  description = "Private IP to connect to EC2 Instances"
}
