from const import PROVIDER, ORGANIZATION
import requests


class Repositories:
    def __init__(self, headers: dict):
        self.repositories = []
        self.headers = headers

    def getRepositories(self):
        resp = None

        try:
            resp = requests.get(
                'https://app.codacy.com/api/v3/organizations/%s/%s/repositories' % (
                    PROVIDER, ORGANIZATION),
                params={
                },
                headers=self.headers)

            resp = resp.json()
            for repository in resp['data']:
                self.repositories.append(repository['name'])

        except Exception as err:
            print("Repositories List Exception: ", err)
            print(resp)

        return self.repositories
