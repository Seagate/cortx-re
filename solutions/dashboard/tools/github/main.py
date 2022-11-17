from rate_limit_check import RateLimitCheck
import os
import asyncio
from const import GITHUB_METADATA_INDEX_IDENTIFIER
from repositories import Repositories
from issues_and_pulls import IssuesAndPulls
from mongodb import MongoDB
from datetime import datetime


class Main:
    def __init__(self):
        pass

    async def main(self):
        print("GitHub Script!!")

        # Github API URL [Do not add '/' at the end of url]
        github_api_url = os.environ.get("GITHUB_API_BASEURL")

        # Github
        github_token = os.environ.get("GITHUB_TOKEN")
        headers = {'Authorization': 'token ' + github_token}

        # MongoDB
        mongodb_credentials = {
            "username": os.environ.get("MONGODB_USERNAME"),
            "password": os.environ.get("MONGODB_PASSWORD")
        }
        mongodb_connection_url = os.environ.get("MONGODB_CONNECTION_URL")

        # today = date.today()
        today = datetime.now()
        today = today.strftime("%d-%b-%Y %H:%M:%S")

        mongodb = MongoDB(today=today)
        mongodb.create_connection_url(
            credentials=mongodb_credentials, connection_url=mongodb_connection_url)
        mongodb.handle_mongodb()

        # Read MongoDB Collections
        # and Create Initialization Documents
        # Because Logstash uses first document from mongodb for initialization
        if not mongodb.read_collection(mongodb.repository_collection):
            mongodb.insertRepositoryInitializationDocuments()
        else:
            print("Not initializing repository document as data found in it")

        if not mongodb.read_collection(mongodb.metadata_collection):
            mongodb.insertMetadataInitializationDocuments()
        else:
            print("Not initializing metadata document as data found in it")

        if not mongodb.read_collection(mongodb.branches_collection):
            mongodb.insertBranchesInitializationDocuments()
        else:
            print("Not initializing branches document as data found in it")

        if not mongodb.read_collection(mongodb.contributors_collection):
            mongodb.insertContributorsInitializationDocuments()
        else:
            print("Not initializing contributors document as data found in it")

        rateLimit = RateLimitCheck(
            headers=headers, github_api_url=github_api_url)
        print("Ok to proceed? : ", rateLimit.checkRateLimit())

        # Repositories, issues and pulls
        issuesAndPullsObject = IssuesAndPulls(
            headers=headers, rateLimit=rateLimit, github_api_url=github_api_url)

        repoObject = Repositories(
            headers=headers,
            rateLimit=rateLimit,
            mongodb=mongodb,
            github_api_url=github_api_url,
            issuesAndPullsObject=issuesAndPullsObject,
            today=today)
        await repoObject.getRepositories()

        metadata = repoObject.getMetadata()
        metadata["identifier"] = GITHUB_METADATA_INDEX_IDENTIFIER
        metadata["created_date"] = today
        metadata["issues"] = [metadata["issues"]]
        metadata["pulls"] = [metadata["pulls"]]
        metadata["branches"] = [metadata["branches"]]
        metadata["contributors"] = [metadata["contributors"]]
        print("\n\n\nMetadata: ", metadata)

        if len(repoObject.repositories) > 0:
            print("")
            mongodb.create_document(
                data=metadata, collection=mongodb.metadata_collection)
            print("DONE")


if __name__ == "__main__":
    mainObject = Main()

    loop = asyncio.get_event_loop()

    # loop.create_task(mainObject.main())
    # loop.run_forever()

    loop.run_until_complete(mainObject.main())
    loop.close()
