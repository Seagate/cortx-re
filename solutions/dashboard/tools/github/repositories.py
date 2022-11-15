import requests
from issues_and_pulls import IssuesAndPulls
from mongodb import MongoDB
from const import GITHUB_REPOSITORY_INDEX_IDENTIFIER, ORGANIZATION
from branch import Branch
from contributors import Contributors
from rate_limit_check import RateLimitCheck
from watchers import Watchers
import asyncio


class Repositories:
    def __init__(self, headers, github_api_url, today, issuesAndPullsObject: IssuesAndPulls,  mongodb: MongoDB, rateLimit: RateLimitCheck):
        self.repositories = []
        self.headers = headers
        self.mongodb = mongodb
        self.github_api_url = github_api_url
        self.rateLimit = rateLimit
        self.issuesAndPullsObject = issuesAndPullsObject
        self.today = today
        self.metadata = {
            "repositories": 0,
            "archived": 0,
            "disabled": 0,
            "forks_count": 0,
            "stargazers_count": 0,
            "watchers_count": 0,
            "allow_forking": 0,
            "issues": {
                "all": 0,
                "open": 0,
                "closed": 0,
            },
            "pulls": {
                "all": 0,
                "open": 0,
                "closed": 0,
            },
            "branches": {
                "all": 0,
                "active": 0,
                "stale": 0,
            },
            "contributors": {
                "all": 0,
                "user": 0,
                "bot": 0,
                "other": 0,
            }
        }

    async def getRepositories(self):
        print("Get Repositories")

        page_no = 1

        try:
            while True:

                print("\n\n-------------------------------------")
                print("Page No : ", page_no)

                while True:
                    rateLimitResp = self.rateLimit.checkRateLimit()
                    if rateLimitResp['status'] == True:
                        break

                    print("Limit Exceeded...waiting for ",
                          rateLimitResp['timeToWait'], " seconds")
                    await asyncio.sleep(rateLimitResp['timeToWait'])

                resp = requests.get(
                    self.github_api_url + "/orgs/%s/repos" % (ORGANIZATION), headers=self.headers, params={
                        "page": page_no,
                        "per_page": "50",
                    })

                if (resp.status_code != 200):
                    print(resp.json())
                    break

                if (len(resp.json()) == 0):
                    break

                data = resp.json()

                repos = []

                repo_cnt = 1
                for repo in data:
                    print(f"\n{page_no} - ({repo_cnt}/{len(data)})")
                    print("Repository: ", repo["name"])

                    # Get Attributes
                    license = "none"
                    language = "none"

                    if (repo["license"] != None):
                        license = repo["license"]["spdx_id"]

                    if ("language" in repo):
                        language = repo["language"]

                    print("Getting Issues and Pulls")
                    issuesPulls = await self.issuesAndPullsObject.getIssuesAndPulls(
                        repository=repo["name"])

                    print("\nIssues: ", issuesPulls["issues"])
                    print("Pulls: ", issuesPulls["pulls"])

                    # Getting branches
                    print("Getting Branches")

                    branchObject = Branch(
                        headers=self.headers,
                        github_api_url=self.github_api_url,
                        repository=repo["name"], rateLimit=self.rateLimit, today=self.today, mongodb=self.mongodb)

                    await branchObject.getBranches()
                    await branchObject.getIndividualBranch()

                    print("Branch Data: ", branchObject.branch_data)
                    print("Branch Meta: ", branchObject.branch_meta)

                    # Getting Contributors Data
                    print("Getting Contributors")
                    contributorObject = Contributors(
                        headers=self.headers,
                        repository=repo["name"],
                        github_api_url=self.github_api_url,
                        today=self.today,
                        mongodb=self.mongodb,
                        rateLimit=self.rateLimit)
                    await contributorObject.getContributors()
                    print("Contributors Data: ", contributorObject.contributors)
                    print("Contributors Meta: ",
                          contributorObject.contributors_meta)

                    # Getting Watchers
                    watchersObject = Watchers(
                        headers=self.headers, github_api_url=self.github_api_url, rateLimit=self.rateLimit)
                    await watchersObject.getWatchers(
                        repository=repo["name"])

                    repoObject = {
                        "repo_id": str(repo["id"]),
                        "repository": repo["name"],
                        "full_name": repo["full_name"],
                        "visibility": repo["visibility"],
                        "default_branch": repo["default_branch"],
                        "archived": repo["archived"],
                        "disabled": repo["disabled"],
                        "has_projects":  repo["has_projects"],
                        "allow_forking":  repo["allow_forking"],
                        "forks_count": repo["forks_count"],
                        "stargazers_count": repo["stargazers_count"],
                        "watchers_count": watchersObject.watchers,
                        "issues": issuesPulls["issues"],
                        "pulls": issuesPulls["pulls"],
                        "license": license,
                        "language": language,
                        "branches": branchObject.branch_meta,
                        "contributors": contributorObject.contributors_meta,
                        "created_at": repo["created_at"],
                        "updated_at": repo["updated_at"],
                        "pushed_at": repo["pushed_at"],
                        "created_date": self.today,
                        "identifier": GITHUB_REPOSITORY_INDEX_IDENTIFIER
                    }

                    # Modify Metadata
                    await self.modifyMetadata(repository=repoObject)

                    # Update schema as per logstash requirements
                    repoObject["issues"] = [issuesPulls["issues"]]
                    repoObject["pulls"] = [issuesPulls["pulls"]]
                    repoObject["branches"] = [branchObject.branch_meta]
                    repoObject["contributors"] = [
                        contributorObject.contributors_meta]

                    # Insert document into mongodb
                    self.mongodb.create_document(
                        data=repoObject, collection=self.mongodb.repository_collection)
                    print("REPOSITORY DOCUMENT DONE")

                    repos.append(repo["name"])
                    repo_cnt += 1

                self.repositories.extend(repos)

                page_no += 1

        except Exception as err:
            print("Exception [GET REPOSITORIES]: ", err)

        return self.repositories

    async def modifyMetadata(self, repository):
        print("Updating Metadata")
        self.metadata["repositories"] += 1
        self.metadata["forks_count"] += repository["forks_count"]
        self.metadata["stargazers_count"] += repository["stargazers_count"]
        self.metadata["watchers_count"] += repository["watchers_count"]

        # Issues
        for key in repository["issues"]:
            if (key in self.metadata["issues"]):
                self.metadata["issues"][key] += repository["issues"][key]
            else:
                self.metadata["issues"][key] = repository["issues"][key]

        # Pulls
        for key in repository["pulls"]:
            if (key in self.metadata["pulls"]):
                self.metadata["pulls"][key] += repository["pulls"][key]
            else:
                self.metadata["pulls"][key] = repository["pulls"][key]

        # Branches
        for key in repository["branches"]:
            if (key in self.metadata["branches"]):
                self.metadata["branches"][key] += repository["branches"][key]
            else:
                self.metadata["branches"][key] = repository["branches"][key]

        # Contributors
        for key in repository["contributors"]:
            if (key in self.metadata["contributors"]):
                self.metadata["contributors"][key] += repository["contributors"][key]
            else:
                self.metadata["contributors"][key] = repository["contributors"][key]

        # Archived
        if (repository["archived"] == True):
            self.metadata["archived"] += 1

        # Disabled
        if (repository["disabled"] == True):
            self.metadata["disabled"] += 1

        # Allow Forking
        if (repository["allow_forking"] == True):
            self.metadata["allow_forking"] += 1

        print("\n\nMetadata Document: ", self.metadata)

    def getMetadata(self):
        return self.metadata
