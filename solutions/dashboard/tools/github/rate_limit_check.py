import requests
import time


class RateLimitCheck:
    def __init__(self, headers, github_api_url):
        self.headers = headers
        self.github_api_url = github_api_url

    def checkRateLimit(self):
        try:
            resp = requests.get(
                self.github_api_url + "/rate_limit", headers=self.headers, params={})

            if (resp.status_code != 200):
                print(resp.json())

            data = resp.json()

            print("{")
            print("LIMIT: ", data['resources']['core']['remaining'], end=", ")
            print("RESET: ", data['resources']['core']['reset'], end=", ")

            date_epoch = int(time.time())
            time_to_reset = data['resources']['core']['reset'] - date_epoch

            print("Time to reset: ", time_to_reset)
            print("}")

            if data['resources']['core']['remaining'] > 1:
                return {"status": True}
            else:
                if time_to_reset >= 1:
                    return {"status": False, "timeToWait": time_to_reset}
                else:
                    return {"status": False, "timeToWait": 1}

        except Exception as err:
            print("Exception [RATE LIMIT CHECK]: ", err)

        return False
