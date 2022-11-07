import os
from issues import Issues
from mongodb import MongoDB
from repositories import Repositories
from datetime import date


class Main:
    def __init__(self):
        pass

    def main(self):

        # Base64
        mongodb_credentials = {
            "username": os.environ.get("MONGODB_USERNAME"),
            "password": os.environ.get("MONGODB_PASSWORD")
        }
        codacy_api_token = os.environ.get("CODACY_API_TOKEN")
        mongodb_connection_url = os.environ.get("MONGODB_CONNECTION_URL")

        # MongoDB
        mongodb = MongoDB()
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

        # Headers
        headers = {
            'Content-Type': 'application/json',
            'Accept': 'application/json',
            'api-token': codacy_api_token
        }

        # Getting repositories list from CODACY
        repos = Repositories(headers=headers)
        repositories = repos.getRepositories()

        print("Repositories: ", repositories)

        # Date and time
        today = date.today()
        main_date = today.strftime("%d-%b-%Y")

        # issues
        issues = Issues(repositories=repositories,
                        mongodb=mongodb,
                        main_date=main_date,
                        headers=headers)
        issues.getIssues()


if __name__ == "__main__":
    main = Main()
    main.main()
