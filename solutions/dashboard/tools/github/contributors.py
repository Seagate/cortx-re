import requests
import asyncio
from rate_limit_check import RateLimitCheck
from mongodb import MongoDB
from const import GITHUB_CONTRIBUTORS_INDEX_IDENTIFIER, ORGANIZATION


class Contributors:
    def __init__(self, headers, repository, github_api_url, today, mongodb: MongoDB, rateLimit=RateLimitCheck):
        self.headers = headers
        self.rateLimit = rateLimit
        self.repository = repository
        self.github_api_url = github_api_url
        self.today = today
        self.mongodb = mongodb
        self.contributors = []
        self.contributors_meta = {
            'all': 0,
            'user': 0,
            'bot': 0,
            'other': 0,
        }

    async def getContributors(self):
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
                    self.github_api_url + "/repos/%s/%s/contributors" % (ORGANIZATION, self.repository), headers=self.headers, params={
                        "page": page_no
                    })

                if (resp.status_code != 200):
                    print(resp.json())
                    break

                if (len(resp.json()) == 0):
                    break

                data = resp.json()

                for contributor in data:
                    if contributor["type"] == "User":
                        self.contributors_meta['user'] += 1
                    elif contributor["type"] == "Bot":
                        self.contributors_meta['bot'] += 1
                    else:
                        self.contributors_meta['other'] += 1

                    self.contributors_meta['all'] += 1

                    local_data = {
                        "unique_identity": self.repository + ":" + contributor["login"],
                        "repository": self.repository,
                        "contributor": contributor["login"],
                        "contributions": contributor["contributions"],
                        "type": contributor["type"],
                        "created_date": self.today,
                        "identifier": GITHUB_CONTRIBUTORS_INDEX_IDENTIFIER
                    }

                    # Insert document into mongodb
                    self.mongodb.create_document(
                        data=local_data, collection=self.mongodb.contributors_collection)
                    print("DONE")

                    self.contributors.append(local_data)

                page_no += 1

        except Exception as err:
            print("Exception [GET REPOSITORIES]: ", err)
