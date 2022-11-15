import requests
import datetime
import asyncio
from rate_limit_check import RateLimitCheck
from const import GITHUB_BRANCHES_INDEX_IDENTIFIER, ORGANIZATION
from mongodb import MongoDB


class Branch:
    def __init__(self, headers, repository, github_api_url, today, mongodb: MongoDB, rateLimit=RateLimitCheck):
        self.headers = headers
        self.rateLimit = rateLimit
        self.repository = repository
        self.github_api_url = github_api_url
        self.mongodb = mongodb
        self.today = today
        self.branches = []
        self.branch_data = []
        self.branch_meta = {
            'all': 0,
            'active': 0,
            'stale': 0,
        }

    async def getBranches(self):
        page_no = 1

        try:
            while True:

                print("Page No : ", page_no)
                while True:
                    rateLimitResp = self.rateLimit.checkRateLimit()
                    if rateLimitResp['status'] == True:
                        break

                    print("Limit Exceeded...waiting for ",
                          rateLimitResp['timeToWait'], " seconds")
                    await asyncio.sleep(rateLimitResp['timeToWait'])

                resp = requests.get(
                    self.github_api_url +
                    "/repos/%s/%s/branches" % (ORGANIZATION, self.repository),
                    headers=self.headers,
                    params={
                        "page": page_no
                    })

                if (resp.status_code != 200):
                    print(resp.status_code, "-", resp.json())
                    break

                if (len(resp.json()) == 0):
                    break

                # Extacting Required Data from Response
                data = resp.json()
                local_branch_data = []

                for item in data:
                    local_branch_data.append(item['name'])

                self.branches.extend(local_branch_data)

                page_no += 1

        except Exception as err:
            print("Exception [GET BRANCHES]: ", err)

    async def getIndividualBranch(self):
        branch_no = 1
        for branch in self.branches:
            print("(", branch_no, "/", len(self.branches), ") = "  "branch: ", branch)
            local_data = {}

            while True:
                rateLimitResp = self.rateLimit.checkRateLimit()
                if rateLimitResp['status'] == True:
                    break

                print("Limit Exceeded...waiting for ",
                      rateLimitResp['timeToWait'], " seconds")
                await asyncio.sleep(rateLimitResp['timeToWait'])

            resp = requests.get(
                self.github_api_url +"/repos/%s/%s/branches/%s" % (ORGANIZATION, self.repository, branch), headers=self.headers, params={
                })

            if (resp.status_code != 200):
                print(resp.status_code, "-", resp.json())
                break

            data = resp.json()
            unique_string = self.repository + ":" + data['name']
            local_data['unique_identity'] = unique_string
            local_data['branch'] = data['name']
            local_data['repository'] = self.repository
            local_data['protected'] = data['protected']
            local_data['last_commit'] = data['commit']['commit']['author']['date']
            local_data['created_date'] = self.today
            local_data['identifier'] = GITHUB_BRANCHES_INDEX_IDENTIFIER

            if data['protected']:
                local_data['protection_enabled'] = data['protection']['enabled']
                if len(data['protection']['required_status_checks']['contexts']) > 0:
                    local_data['protection'] = data['protection']['required_status_checks']['contexts']
                else:
                    local_data['protection'] = ["none"]
            else:
                local_data['protection_enabled'] = False
                local_data['protection'] = ["none"]

            # Check whether branch is stale or not
            last_commit = data['commit']['commit']['author']['date']
            last_commit_time = datetime.datetime.strptime(
                last_commit, "%Y-%m-%dT%H:%M:%SZ")
            time_now = datetime.datetime.now()

            age = time_now - last_commit_time
            if age > datetime.timedelta(days=91.2501):
                local_data['status'] = "Stale"
                self.branch_meta['stale'] += 1
            else:
                local_data['status'] = "Active"
                self.branch_meta['active'] += 1

            self.branch_meta['all'] += 1

            # Insert document into mongodb
            self.mongodb.create_document(
                data=local_data, collection=self.mongodb.branches_collection)
            print("DONE")

            self.branch_data.append(local_data)

            branch_no += 1
