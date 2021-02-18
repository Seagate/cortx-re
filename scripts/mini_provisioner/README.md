# Cortx Mini-Provisioning

## Purpose
 This playbook acts as a wrapper for cortx component mini-provisioning and provides flexibility to deploy & validate the specific component on a single click.
 
## Cortx components
 - [cortx-motr](roles/motr/README.md)
 - [cortx-s3server](roles/s3server/README.md)
 - [cortx-hare](roles/hare/README.md)
 - [cortx-monitor](roles/sspl/README.md)
 - [cortx-manager](roles/csm/README.md)
 - [cortx-utils](roles/pyutils/README.md)
 
 ## What it Does

- Perform prereq for deployment
    - Enabling passwordless SSH between deploy node and executor machine
- Executes deployment steps provided in the component mini-provisioning wiki


## How it works

This deployment wrapper was developed in ansible. so calling ansible playbook with required deployment parameters will start the deployment process.


### Prerequisite

Install Ansible on the execution host
```
yum install -y ansible
```

Clone cortx-re repo and navigate to mini-provisioner folder
```
    git clone https://github.com/Seagate/cortx-re -b main
    cd cortx-re/scripts/mini_provisioner
```

#### Motr deployment
``` 
    ansible-playbook motr_deploy.yml --extra-vars "NODE1=<CLEAN_VM_FQDN> CORTX_BUILD=<CORXT_BUILD_HTTP_URL>"
```

#### S3server deployment
``` 
    ansible-playbook s3server_deploy.yml --extra-vars "NODE1=<CLEAN_VM_FQDN> CORTX_BUILD=<CORXT_BUILD_HTTP_URL>" 
```

#### Hare deployment
``` 
    ansible-playbook hare_deploy.yml --extra-vars "NODE1=<CLEAN_VM_FQDN> CORTX_BUILD=<CORXT_BUILD_HTTP_URL>" 
```

#### Monitor deployment
``` 
    ansible-playbook sspl_deploy.yml --extra-vars "NODE1=<CLEAN_VM_FQDN> CORTX_BUILD=<CORXT_BUILD_HTTP_URL>" 
```

#### Manager deployment
``` 
    ansible-playbook csm_deploy.yml --extra-vars "NODE1=<CLEAN_VM_FQDN> CORTX_BUILD=<CORXT_BUILD_HTTP_URL>" 
```

#### PyUtils deployment
``` 
    ansible-playbook pyutils_deploy.yml --extra-vars "NODE1=<CLEAN_VM_FQDN> CORTX_BUILD=<CORXT_BUILD_HTTP_URL>" 
```


#### Directory Structure

```
├── README.md
├── ansible.cfg
├── inventories
│   └── hosts
├── motr_deploy.yml
├── s3server_deploy.yml
├── hare_deploy.yml
├── csm_deploy.yml
|── sspl_deploy.yml
├── prepare.yml
├── pyutils_deploy.yml
└── roles
    ├── 00_prepare_environment
    │   ├── files
    │   │   ├── passwordless_ssh.sh
    │   │   ├── reimage.sh
    │   │   └── update_hosts.sh
    │   └── tasks
    │       ├── 01_prepare_env.yml
    │       ├── 02_reimage.yml
    │       ├── main.yml
    │       └── passwordless_authentication.yml
    ├── csm
    │   ├── tasks
    │   │   └── main.yml
    │   ├── templates
    │   │   └── cortx.repo.j2
    │   └── vars
    │       └── config.yml
    ├── hare
    │   ├── files
    │   │   └── lnet.conf
    │   ├── tasks
    │   │   ├── 01_install_prerequisites.yml
    │   │   ├── 02_mini_provisioning.yml
    │   │   └── main.yml
    │   ├── templates
    │   │   ├── confstore.json.j2
    │   │   └── cortx.repo.j2
    │   └── vars
    │       └── config.yml
    ├── motr
    │   ├── files
    │   │   ├── reset_machineid.sh
    │   │   └── run_io_tests.sh
    │   ├── tasks
    │   │   ├── 01_install_prerequisites.yml
    │   │   ├── 02_mini_provisioning.yml
    │   │   ├── 03_bootstrap_cluster..yml
    │   │   ├── 04_validate.yml
    │   │   └── main.yml
    │   ├── templates
    │   │   ├── confstore.json.j2
    │   │   ├── cortx.repo.j2
    │   │   └── singlenode.yml.j2
    │   └── vars
    │       └── config.yml
    ├── pyutils
    │   ├── files
    │   │   └── install_kafka.sh
    │   ├── tasks
    │   │   └── main.yml
    │   ├── templates
    │   │   ├── confstore.json.j2
    │   │   └── cortx.repo.j2
    │   └── vars
    │       └── config.yml
    ├── s3server
    │   ├── files
    │   │   ├── lnet.conf
    │   │   └── install_configure_kafka.sh
    │   ├── tasks
    │   │   ├── 01_install_prerequisites.yml
    │   │   ├── 02_install_s3server.yml
    │   │   ├── 03_mini_provisioning.yml
    │   │   ├── 04_start_s3server.yml
    │   │   ├── 05_validate.yml
    │   │   └── main.yml
    │   ├── templates
    │   │   ├── confstore.json.j2
    │   │   ├── cortx.repo.j2
    │   │   └── singlenode.yml.j2
    │   └── vars
    │       └── config.yml
    └── sspl
        ├── tasks
        │   └── main.yml
        └── vars
           └── config.yml
```