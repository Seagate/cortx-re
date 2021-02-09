from jira import JIRA
from github_release import gh_release_create
import shutil
import os
import argparse
import json
import sys

#parse command line arguments
parser = argparse.ArgumentParser()
parser.add_argument('-u', dest='username', type=str, help='JIRA username')
parser.add_argument('-p', dest='password', type=str, help='JIRA password')
parser.add_argument('--query', dest='query', type=str, help='JIRA query')
parser.add_argument('--build', dest='ova_build_number', type=str, help='OVA build number')
parser.add_argument('--highlight', dest='highlight', nargs='+', default=[])
args = parser.parse_args()

#variables
issue_exclude_filter = ['ldr','ldrr2','lr2','ldr2','r2','plan','planning','estimate','estimation','explore']
sem_ver="1.0."+ str(os.environ['BUILD_NUMBER'])
old_ova_path = '/root/cortx-ova-build-'+ args.ova_build_number +'.ova'
new_ova_path = '/root/cortx-'+sem_ver+'.ova'
highlight_issues = args.highlight
releasenotes = ""

#collect jira issues data and create release-notes
jira = JIRA(basic_auth=(args.username, args.password), options={'server':'https://jts.seagate.com'})

jql = args.query
block_num = 0
block_size = 100
#loop over issues
while True:

    start_idx = block_num * block_size
    if block_num == 0:
        issues = jira.search_issues(jql, start_idx, block_size)
    else:
        more_issue = jira.search_issues(jql, start_idx, block_size)
        if len(more_issue)>0:
            for x in more_issue:
                issues.append(x)
        else:
            break
    if len(issues) == 0:
        # Retrieve issues until there are no more to come
        break
    block_num += 1

print(len(issues))

releasenotes = "## Features\n\n"
for issue in issues:
   if issue.fields.issuetype.name in ['Task','Story'] and not any(word in issue.fields.summary.lower() for word in issue_exclude_filter) and issue.key not in highlight_issues and issue.fields.components:
      print(issue.fields.components[0].name) 
      releasenotes+=str("- {} : {} [[{}]]({})\n".format(issue.fields.components[0],issue.fields.summary,issue.key,"https://jts.seagate.com/browse/"+issue.key))

releasenotes+="\n"
releasenotes+="## Bugfixes\n\n"
for issue in issues:
   if issue.fields.issuetype.name in ['Bug'] and not any(word in issue.fields.summary.lower() for word in issue_exclude_filter) and issue.key not in highlight_issues and issue.fields.components:
      releasenotes+=str("- {} : {} [[{}]]({})\n".format(issue.fields.components[0].name,issue.fields.summary,issue.key,"https://jts.seagate.com/browse/"+issue.key))

if highlight_issues:
   releasenotes+="\n"
   releasenotes+="## Highlights\n\n"
   for issue_id in highlight_issues:
       for issue in issues:
          if issue_id == issue.key and issue.fields.components:
             releasenotes+=str("- {} : {} [[{}]]({})\n".format(issue.fields.components[0].name,issue.fields.summary,issue.key,"https://jts.seagate.com/browse/"+issue.key))

print(releasenotes)
#publish ova release with release-notes if required ova file is present
if os.path.isfile(old_ova_path):
   shutil.copyfile(old_ova_path,new_ova_path)
   gh_release_create("Seagate/cortx","VA-"+sem_ver,publish=True,body=releasenotes,name="Virtual Appliance "+sem_ver,asset_pattern=new_ova_path)
else:
   sys.exit("ERROR: Required OVA build is not available")
