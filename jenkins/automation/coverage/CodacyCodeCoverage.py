import json
import requests
from json2html import *
import sys

component = sys.argv[0]
auth = sys.argv[1]

if len(component) <= 1:
    print('Error: Usage: python3 fetch_codacy_info.py <component name>')
    exit(1)

headers = {
  'Accept': 'application/json',
  'api-token': auth
}

urlink = ("https://app.codacy.com/api/v3/analysis/organizations/gh/Seagate/repositories/%s/commit-statistics?days=5" %(component))

r = requests.get(urlink, params={}, headers = headers)

data = r.json()
commitID = [i['commitId'] for i in data['data'] if 'commitId' in i]
coverage = [i['coveragePercentage'] for i in data['data']  if 'coveragePercentage' in i]
issuepercentage = [i['issuePercentage'] for i in data['data'] if 'issuePercentage' in i]
complexity = [i['complexFilesPercentage'] for i in data['data'] if 'complexFilesPercentage' in i]
duplication = [i['duplicationPercentage'] for i in data['data'] if 'duplicationPercentage' in i]

d = {'data': [{'commitID': cID, 'coverage': c, 'issuepercentage': i, 'complexity': co, 'duplication': d} for cID, c, i, co, d in zip(commitID, coverage, issuepercentage, complexity, duplication)]}
filterdata = json.dumps(d, indent=4)
i = json2html.convert(json = filterdata)
print(i)

