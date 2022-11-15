import requests
from rate_limit_check import RateLimitCheck
import asyncio


class IssuesAndPulls:
    def __init__(self, headers, github_api_url, rateLimit=RateLimitCheck):
        self.headers = headers
        self.rateLimit = rateLimit
        self.github_api_url=github_api_url

    async def getIssuesAndPulls(self, repository):
        print("Get Issues and Pulls")

        issue_types = {
            "all": 0,
            "open": 0,
            "closed": 0
        }
        pull_types = {
            "all": 0,
            "open": 0,
            "closed": 0
        }

        page_no = 1

        try:
            print("Page No : ", end=" ")

            while True:
                print(page_no, end=" ")

                while True:
                    rateLimitResp = self.rateLimit.checkRateLimit()
                    if rateLimitResp['status'] == True:
                        break

                    print("Limit Exceeded...waiting for ",
                          rateLimitResp['timeToWait'], " seconds")
                    await asyncio.sleep(rateLimitResp['timeToWait'])

                resp = requests.get(
                    self.github_api_url + "/repos/Seagate/%s/issues" % (
                        repository),
                    headers=self.headers, params={
                        "state": "all",
                        "per_page": "50",
                        "page": page_no
                    })

                if (resp.status_code != 200):
                    print("Error: ", resp.json())
                    break

                if len(resp.json()) == 0:
                    break

                for item in resp.json():
                    if ("pull_request" not in item):
                        if (item["state"] in issue_types):
                            issue_types[item["state"]] += 1
                        else:
                            issue_types[item["state"]] = 1

                        issue_types["all"] += 1

                    else:
                        if (item["state"] in pull_types):
                            pull_types[item["state"]] += 1
                        else:
                            pull_types[item["state"]] = 1

                        pull_types["all"] += 1
                page_no += 1

        except Exception as err:
            print("Error: ", err)

        return {"issues": issue_types, "pulls": pull_types}
