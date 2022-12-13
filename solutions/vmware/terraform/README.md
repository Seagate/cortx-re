# How to create Deployment/VM resources in VMware vRealize using Terraform

## Pre-requisites
Terraform should be installed on VM environment.

Terraform Installation Reference - https://developer.hashicorp.com/terraform/downloads

## Procedure
* Clone `cortx-re` repository and go to directory `solutions/vmware/terraform`. 
    
  ```
  git clone https://github.com/Seagate/cortx-re && cd $PWD/cortx-re/solutions/vmware/terraform
  ```
* Update `terraform.tfvars` file with required values as per your infrastructure requirements.
  * How to get refresh token - 
    
    ```
    curl -k -X POST https://<vra-fqdn>/csp/gateway/am/api/login?access_token -H 'Content-Type: application/json' -d '{ "username":"", "password":"" }'
    ```
    
  * How to get project name and catalog item -
    
  ![](vRealize_project_catalog.PNG)
    
  * How to get catalog item version -
    1. Get catalog item
    
    ```
    curl -k -X GET https://<vra-fqdn>/catalog/api/items -H "Authorization: Bearer <TOKEN>"
    ```
    2. Get required catalog item version
    
    ```
    curl -k -X GET https://<vra-fqdn>/catalog/api/items/<Catalog-Item-ID>/versions -H "Authorization: Bearer <TOKEN>"  
    ```
* Initialize a terraform working directory.  

  ```
  terraform init
  ```
* Validate the terraform configuration files(verify whether a configuration is syntactically valid and internally consistent, regardless of any provided variables or existing state) in a directory.

  ```
  terraform validate
  ```
* Preview and approve the changes that Terraform plans to make to your infrastructure.

  ```
  terraform plan
  ```
* Execute the actions proposed in a Terraform plan

  ```
  terraform apply
  ```
