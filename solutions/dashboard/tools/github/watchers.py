import requests
import asyncio
from rate_limit_check import RateLimitCheck
from const import ORGANIZATION


class Watchers:
    def __init__(self, headers, github_api_url, rateLimit=RateLimitCheck):
        self.headers = headers
        self.rateLimit = rateLimit
        self.github_api_url = github_api_url
        self.watchers = 0

    async def getWatchers(self, repository):
        page_no = 1

        try:
            while True:
                print("Page No : ", page_no)

                while True:
                    rateLimitResp = self.rateLimit.checkRateLimit()
                    if rateLimitResp['status'] is True:
                        break

                    print("Limit Exceeded...waiting for ",
                          rateLimitResp['timeToWait'], " seconds")
                    await asyncio.sleep(rateLimitResp['timeToWait'])

                resp = requests.get(
                    self.github_api_url + "/repos/%s/%s/subscribers" % (ORGANIZATION, repository), headers=self.headers, params={
                        "page": page_no
                    })

                if (resp.status_code != 200):
                    print(resp.json())
                    break

                if (len(resp.json()) == 0):
                    break

                data = resp.json()

                self.watchers += len(data)

                page_no += 1

        except Exception as err:
            print("Exception [GET WATCHERS]: ", err)
