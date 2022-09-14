[![Codacy Badge](https://app.codacy.com/project/badge/Grade/f20f5b6fe8aa4efabb629cf8f62a5d73)](https://www.codacy.com/gh/Seagate/cortx-re/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=Seagate/cortx-re&amp;utm_campaign=Badge_Grade)
[![DCO Check](https://github.com/Seagate/cortx-re/actions/workflows/dco-check.yml/badge.svg)](https://github.com/Seagate/cortx-re/actions/workflows/dco-check.yml)
[![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://github.com/Seagate/cortx-re/blob/main/LICENSE)
[![YouTube Badge](https://img.shields.io/badge/Video-YouTube-red)](https://cortx.link/videos )
[![Slack](https://img.shields.io/badge/chat-on%20Slack-blue")](https://cortx.link/join-slack)
[![GitHub contributors](https://img.shields.io/github/contributors/Seagate/cortx-re)](https://github.com/Seagate/cortx-re/graphs/contributors/)

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

## Documents 

All RE related documents are available in the below locations
-   [RE GitHub Wiki](https://github.com/Seagate/cortx-re/wiki)
