[![Codacy Badge](https://app.codacy.com/project/badge/Grade/edb2670e6aa24aeb899c496c15b596c9)](https://www.codacy.com/gh/Seagate/cortx-re/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=Seagate/cortx-re&amp;utm_campaign=Badge_Grade)
[![license](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/Seagate/cortx/blob/main/LICENSE)
[![Slack](https://img.shields.io/badge/chat-on%20Slack-blue")](https://cortx.link/join-slack)
[![YouTube](https://img.shields.io/badge/Video-YouTube-red)](https://cortx.link/videos)
[![Latest Release](https://img.shields.io/github/v/release/Seagate/cortx?label=Latest%20Release)](https://github.com/seagate/cortx/releases/latest)
[![GitHub contributors](https://img.shields.io/github/contributors/Seagate/cortx-re)](https://github.com/Seagate/cortx-re/graphs/contributors/)
[![SODA Eco project](https://img.shields.io/badge/SODA-ECO%20Project-9cf)](./doc/Soda-welcome-page.md)

# CORTX DevOps and Release Engineering
The purpose of this repository is to keep the scripts, tools, and other configuration used in the CORTX DevOps and Release Engineering. 

DevOps and Release Engineering team is responsible for the followings

-   CORTX Project CI/CD Build Process.
-   Automation to support CORTX project.
-   Manage DevOps Tools.

## Repo Structure

An overview of folder structure in cortx-re repo
```console
├───docker
├───jenkins
└───scripts
└───solutions

```
### Docker
-   We have containerized Release engineering infrastructure to eliminate host and OS dependency. The dockerfiles, docker-compose files used to build this containers are available under this folder.

### Jenkins
-   Jenkins job configurations, groovy scripts and template used in the Jenkins are available under this folder.

### Scripts
-   Shell, python scripts used in the RE process automation are available under this folder.
-   Scripts like changelog generation, build manifest generation, rpm validation..etc  are available under this folder

### Solutions
-   Solution specific scripts, configuration files. 
-   CORTX deployment

    1. Automated scripts: [LINK](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/CORTX-Deployment.md)

    2. On AWS Using Terraform: [LINK](https://github.com/Seagate/cortx-re/blob/main/solutions/community-deploy/cloud/AWS/README.md)

## Documents 

All RE related documents are available in the below locations
-   [RE GitHub Wiki](https://github.com/Seagate/cortx-re/wiki)
