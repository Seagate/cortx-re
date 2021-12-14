[![Codacy Badge](https://app.codacy.com/project/badge/Grade/edb2670e6aa24aeb899c496c15b596c9)](https://www.codacy.com?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=Seagate/cortx-re&amp;utm_campaign=Badge_Grade) [![License: AGPL v3](https://img.shields.io/badge/License-AGPL%20v3-blue.svg)](https://github.com/Seagate/cortx-re/blob/main/LICENSE)

# CORTX DevOps and Release Engineering
The purpose of this repository is to keep the scripts, tools, and other configuration used in the CORTX DevOps and Release Engineering. 

DevOps and Release Engineering team is responsible for the followings

-   CORTX Project CI Build Process.
-   Managing Artifact storage and Docker regsirty hosting.
-   Automation to support cortx project.

## Repo Structure

An overview of folder structure in cortx-rerepo
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

## Support
The below table explains  RE support process.

| Type                      |  Priority     | ETA                                                       |
|---------------------------|---------------|-----------------------------------------------------------|
| New Requirement           | Major/Minor   | Groomed -> Planed -> Implemented in next immediate sprint |
| Bug                       | Minor         | Addressed in next immediate sprint                        |
| Bug                       | Major         | Addressed ASAP                                            |
| KT/Clarification/Demo     | minor         | Based on RE Engineer availability                         |


## Documents 

All RE related documents are available in the below locations
-   [RE GitHub Wiki](https://github.com/Seagate/cortx-re/wiki)
