This is a Ansible Playbook written for collecting installed packages from Cloudform VM's and install them on VMware VM's 
There are two roles available mentioned as below -
1. get_metadata - Gather/collect packages from given list of VM's
2. install_packages - Install gathered/collected packages on given list of VM's

## Pre-requisites
1. Install ansible on local VM
2. Clone repository and goto `solutions/vmware/migration/ansible/vm-metadata-operations` directory
3. Update hostnames/IP in `inventories/dev/hosts` file.
4. Search `<Fresh Rocky Linux Cloudform VM>` and replace it with Cloudform VM hostname/IP in file `roles/get_metadata/tasks/main.yml`

## How to run a playbook
```
ansible-playbook -i inventories/dev <get_packages.yml | install_packages.yml> --user <GID> --ask-pass --ask-become-pass
```
