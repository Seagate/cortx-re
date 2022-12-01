#!/bin/bash

function add_common_separator() {
    echo -e '\n--------------- '"$*"' ---------------\n'
}

function check_status() {
    return_code=$?
    error_message=$1
    if [ $return_code -ne 0 ]; then
            add_common_separator ERROR: $error_message
            exit 1
    fi
    add_common_separator SUCCESS
}

function usage() {
    cat << HEREDOC
Usage : $0 [--config, --validate, --provision-resources]
where,
    --config - Cleanup stale files and create a terraform variables file with user provided requirements.
    --validate - Validate terraform configuration and verify changes going to happen in infrastructure.
    --provision-resources - Create resources as requested/planned
HEREDOC
}

function cleanup() {
    files_to_remove=(
      .terraform
      .terraform.lock.hcl
      terraform.tfstate
      terraform.tfstate.backup
      terraform.tfvars
    )
    for file in "${files_to_remove[@]}"; do
      if [ -f "$file" ] || [ -d "$file" ]; then
        echo "Removing file/folder $file"
        rm -rf "$file"
      fi
    done
}

function get_token() {
    USERNAME=$1
    PASSWORD=$2
    token=$( curl -sk -X POST https://ssc-vra.colo.seagate.com/csp/gateway/am/api/login?access_token -H 'Content-Type: application/json' -d "{ \"username\":\"$USERNAME\", \"password\":\"$PASSWORD\" }" | jq -r .refresh_token )
    if [ ${#token} -ne 32 ]; then echo "$token" && exit 1; fi
    echo ${token}
}

function set_variable() {
    echo "$1" >> terraform.tfvars
}

function terraform_variables_config() {

    if [ -z "$VRA_USERNAME" ] && [ -z "$VRA_PASSWORD" ]; then echo "USERNAME/PASSWORD not provided. Please provide vra username/password" && exit 1; fi
    if [ -z "$VM_NAMES" ]; then echo "VM_NAMES not provided. Please provide VM names/names" && exit 1; else set_variable "vm_names = [\"$VM_NAMES\"]"; fi
    if [ -z "$VM_CPU" ]; then echo "VM_CPU not provided. Using default: 4"; else set_variable "vm_resources.cpu = $VM_CPU"; fi
    if [ -z "$VM_MEMORY" ]; then echo "VM_MEMORY not provided. Using default: 2048"; else set_variable "vm_resources.memory = $VM_MEMORY"; fi
    if [ -z "$VM_DISKCOUNT" ]; then echo "VM_DISKCOUNT not provided. Using default : 4"; else set_variable "vm_resources.disk_count = $VM_DISKCOUNT"; fi
    if [ -z "$VM_DISKSIZE" ]; then echo "VM_DISKSIZE not provided. Using default : 10"; else set_variable "vm_resources.disk_size = $VM_DISKSIZE"; fi
    REFRESH_TOKEN=$(get_token $VRA_USERNAME $VRA_PASSWORD) && set_variable "vra_refresh_token = \"$REFRESH_TOKEN\""
}

function validate_terraform_config() {
    terraform validate
    check_status "ERROR: Terraform Validation Failed!!"
    terraform plan
    check_status "ERROR: Terraform Plan Failed!!"
}


ACTION="$1"
if [ -z "$ACTION" ]; then
    echo "ERROR : No option provided"
    usage
    exit 1
fi

case $ACTION in
    --config)
        cleanup
        terraform init
        check_status "ERROR: Terraform Init Failed!!"
        terraform_variables_config
    ;;
    --validate)
        validate_terraform_config
    ;;
    --provision-resources)
        terraform apply -auto-approve
        check_status "ERROR: Infrastructure Provisioning Failed!!"
    ;;
    *)
        echo "ERROR : Please provide a valid option"
        usage
        exit 1
    ;;
esac