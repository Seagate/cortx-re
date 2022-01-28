# Automated pre-release with release notes generation for OVA

## Description
Create a OVA pre-release in [`Seagate/cortx`](https://github.com/Seagate/cortx) repository with release-notes includes features, bugfixes and general changes happened since last release.

## How to create OVA pre-release with release-notes
1. Install below dependencies.
  
  certifi==2020.12.5
  cffi==1.14.4
  chardet==4.0.0
  click==7.1.2
  cryptography==3.4.3
  defusedxml==0.6.0
  githubrelease==1.5.8
  idna==2.10
  jira==2.0.0
  LinkHeader==0.4.3
  oauthlib==3.1.0
  pbr==5.5.1
  pycparser==2.20
  PyJWT==2.0.1
  requests==2.25.1
  requests-oauthlib==1.3.0
  requests-toolbelt==0.9.1
  six==1.15.0
  urllib3==1.26.8


2. Run `ova_release.py` script with required arguments as follow
  
```
python3 ova_release.py -u <JIRA Username> -p <JIRA Password> --build <OVA Build Number> --release <GitHub Release Number> --sourceBuild <CORTX Build    Number> --targetBuild <CORTX Build Number>
```
  
  Parameters:
    
    -u : JIRA Username
    
    -p : JIRA Password
    
    --build : OVA Build number (Internally pre-fix build number with `cortx-ova-build` e.g. `cortx-ova-build-<Build Number>`)
    
    --release : GitHub pre-release version number
    
    --sourceBuild : CORTX build number used to build previous OVA release
    
    --targetBuild : CORTX build number used to build current OVA release
