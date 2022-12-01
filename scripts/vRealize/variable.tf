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

variable "vm_resources" {
  type = map
  default = {
      "image" = "Rocky-8-Library-VMs"
      "cpu" = 4
      "memory" = 2048
      "disk_size" = 10
      "disk_count" = 4
  }
}