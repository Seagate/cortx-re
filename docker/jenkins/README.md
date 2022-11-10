# Jenkins As Continuous Integration Tool

We use Jenkins as Continuous Integration tool. This document explains Jenkins deployment architecture and associated use cases.  

## Overview 

-  Jenkins server is deployed as Docker container on Virtual Machine. Jenkins configuration data i.e. `jenkins_home` directory is volume mounted from Virtual Machine which inturn mounted from remote NFS server. Hence actual data is stored on NFS Sever and not on Container or VM. We can bring up Jenkins on different node by configuring NFS mount and starting Jenkins container.
-  Jenkins Container deployment configuration is available as code in docker-compose configuration file. Jenkins can be brought up on any vm with `docker-compose up` in case of VM failure. 
-  Dynamically generated Docker containers are used as Jenkins worker nodes. Docker Swarm is installed on VM cluster and Jenkins Docker Swarm plugin is used to create run time containers. This enables supporting multiple OS for component builds, easy scale-up and ephemeral build systems.
-  Dynamic Containers are used as jump host for Kubernetes/CORTX deployments and any custom automation's. We do not need to attach Virtual Machines/Hardware nodes as Jenkins workers in-order to deploy Kubernetes/CORTX or any other custom automation's. Jenkins uses ephemeral container to connect to provided Virtual Machines/Hardware nodes using user provided credentials. 

## Architecture Diagram

![Jenkins Deployment](./jenkins_arch.png)

## Tools Used 

* [Jenkins](https://www.jenkins.io/)
* [Docker Community Edition](https://docs.docker.com/engine/install/centos/)
* [Docker Swarm](https://docs.docker.com/engine/swarm/)
* [Docker Compose](https://docs.docker.com/compose/)
* Virtual machines on RedHat Virtualization