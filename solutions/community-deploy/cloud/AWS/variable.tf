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
variable "security_group_cidr" {
  description = "Value of CIDR block to be used for Security Group. This should be your systems public-ip"
  type = string
}


variable "os_version" {
  description = "OS version"
  type = string
}


variable "region" {
  description = "Region"
  type = string
}

variable "key_name" {
  description = "SSH key name"
  type = string
  default = "cortx-key"
}

variable "instance_count" {
  description = "EC2 instance count"
  type = number
  default = "1"
}

variable "ebs_volume_count" {
  description = "EBS volumes to attach onto nodes"
  type = number
  default = "9"
}

variable "ebs_volume_size" {
  description = "EBS volumes size in GB"
  type = number
  default = "10"
}

variable "tag_name" {
  description = "TAG name for multi instances"
  type = string
  default = "cortx-multinode"
}

variable "ec2_device_names" {
  description = "Available block devices to attach to instances."
  type = list(string)
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
    "/dev/sdk",
    "/dev/sdl",
    "/dev/sdm",
    "/dev/sdn",
    "/dev/sdo",
    "/dev/sdp",
    "/dev/sdq",
    "/dev/sdr",
    "/dev/sds",
    "/dev/sdt",
    "/dev/sdu",
    "/dev/sdv",
    "/dev/sdw",
    "/dev/sdx",
    "/dev/sdy",
    "/dev/sdz"
  ]
}
