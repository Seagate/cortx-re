variable "owner_account" {
  type = string
  default = "EOS_SVC_RE1"
}

variable "vra_url" {
  type = string
  default = "https://ssc-vra.colo.seagate.com"
}

variable "vra_project" {
  type = string
  default = "SSC-CICD"
}

variable "vra_catalog_item" {
  type = string
  default = "ssc-cicd-rocky"
}

variable "vra_refresh_token" {
  type = string
}

variable "catalog_item_version" {
  type = number
  default = 5
}

variable "vm_names" {
  type = list(string)
}

variable "catalog_item_version" {
  type = number
  default = 5
}

variable "vm_image" {
  type = string
  default = "Rocky-8-Library-VMs"
}

variable "vm_cpu" {
  type = number
  default = 4
}

variable "vm_memory" {
  type = number
  default = 2048
}

variable "vm_disk_size" {
  type = number
  default = 10
}

variable "vm_disk_count" {
  type = number
  default = 4
}