import os
import asyncio
from issues import Issues
from const import MONGODB_CONNECTION_URL, HEADERS
from mongodb import MongoDB
from repositories import Repositories
from kubernetes import config, client

class Main:
  def __init__(self):
    pass

  async def main(self):
    
    # Base64
    mongodb_credentials = {
        "username": os.environ.get("MONGODB_USERNAME"),
        "password": os.environ.get("MONGODB_PASSWORD")
    } 
    codacy_api_token = os.environ.get("CODACY_API_TOKEN")
    
    # Kubernetes Initialization
    config.load_incluster_config()

    # MongoDB
    mongodb = MongoDB()
    mongodb.create_connection_url(credentials=mongodb_credentials, connection_url=MONGODB_CONNECTION_URL)
    mongodb.handle_mongodb()
    # Create Initialization Documents
    # Because Logstash uses first document from mongodb for initialization
    mongodb.insertInitializationDocuments()

    # Headers
    headers = {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        'api-token': codacy_api_token
    }


    # Repositories
    while True:
      repos = Repositories(headers=headers)
      repositories = repos.getRepositories()
      repositories.sort()
      print(repositories)

      # All issues
      issues = Issues(repositories=repositories, mongodb=mongodb, headers=headers)
      issues.handle_issues()

      await asyncio.sleep(86400)

if __name__ == "__main__":
  main = Main()

  loop = asyncio.get_event_loop()
  loop.create_task(main.main())
  loop.run_forever()
  