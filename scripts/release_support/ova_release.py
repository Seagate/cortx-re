from atlassian import Jira
from github_release import gh_release_create
import shutil
import os
import argparse
import json
import sys
reload(sys)
sys.setdefaultencoding('utf8')

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

#collect sprint data and create release-notes
jira = Jira(
    url='https://jts.seagate.com/',
    username=args.username,
    password=args.password)
data = jira.jql(args.query)
data = json.loads(json.dumps(data))
releasenotes = "## Features\n\n"
for issue in data['issues']:
   if issue['fields']['issuetype']['name'] in ['Task','Story'] and not any(word in issue['fields']['summary'].lower() for word in issue_exclude_filter) and issue['key'] not in highlight_issues:
         releasenotes+=str("- {} : {}\n".format(issue['fields']['components'][0]['name'],issue['fields']['summary']))

releasenotes+="\n"
releasenotes+="## Bugfixes\n\n"
for issue in data['issues']:
   if issue['fields']['issuetype']['name'] in ['Bug'] and not any(word in issue['fields']['summary'].lower() for word in issue_exclude_filter) and issue['key'] not in highlight_issues:
          releasenotes+=str("- {} : {}\n".format(issue['fields']['components'][0]['name'],issue['fields']['summary']))

if highlight_issues:
   releasenotes+="\n"
   releasenotes+="## Highlights\n\n"
   for issue_id in highlight_issues:
       for issue in data['issues']:
          if issue_id == issue['key']:
             releasenotes+=str("- {} : {}\n".format(issue['fields']['components'][0]['name'],issue['fields']['summary']))

print(releasenotes)
publish ova release with release-notes if required ova file is present
if os.path.isfile(old_ova_path):
   shutil.copyfile(old_ova_path,new_ova_path)
   gh_release_create("gauravchaudhari02/cortx","VA-"+sem_ver,publish=True,body=releasenotes,name="Virtual Appliance "+sem_ver,asset_pattern=new_ova_path)
else:
   sys.exit("ERROR: Required OVA build is not available")
