This is a Ansible Playbook written for collecting installed packages from Cloudform VM's and install them on VMware VM's 
There are two roles available mentioned as below -
1. get_metadata - Gather/collect packages from given list of VM's
2. install_packages - Install gathered/collected packages or tools on given list of VM's
   Tags -
   1. install-packages - install packages gathered/collected from `get_metadata` role.
   2. install-docker - install/enable docker service and configure internal docker registry
    

## Pre-requisites
1. Install ansible on local VM
2. Clone repository and goto `solutions/vmware/migration/ansible/vm-metadata-operations` directory
3. Update hostnames/IP in `inventories/dev/hosts` file.
4. Search `<Fresh Rocky Linux Cloudform VM>` and replace it with Cloudform VM hostname/IP in file `roles/get_metadata/tasks/main.yml`

## How to run a playbook

To fetch installed packages data - 
```
ansible-playbook -i inventories/dev get_packages.yml --user <GID> --ask-pass --ask-become-pass
```

To install packages / tools -
```
ansible-playbook -i inventories/dev install_packages.yml --tags <install-packages | install-docker> --user <GID> --ask-pass --ask-become-pass
```
