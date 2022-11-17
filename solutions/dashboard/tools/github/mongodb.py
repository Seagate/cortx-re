from const import GITHUB_REPOSITORY_INDEX_IDENTIFIER, GITHUB_METADATA_INDEX_IDENTIFIER, MONGODB_DB_NAME
from const import GITHUB_BRANCHES_INDEX_IDENTIFIER, GITHUB_CONTRIBUTORS_INDEX_IDENTIFIER
from const import MONGODB_GITHUB_REPOSITORIES_COLLECTION_NAME, MONGODB_GITHUB_METADATA_COLLECTION_NAME
from const import MONGODB_GITHUB_BRANCHES_COLLECTION_NAME, MONGODB_GITHUB_CONTRIBUTORS_COLLECTION_NAME
from datetime import datetime
from pymongo import MongoClient


class MongoDB:
    def __init__(self, today) -> None:
        self.mongo_client = None
        self.db = None
        self.repository_collection = None
        self.metadata_collection = None
        self.document_id = None

        self.today = today

        self.connection_url = None

    def handle_mongodb(self):
        self.initialize_mongodb()

    def create_connection_url(self, credentials: dict, connection_url):
        prefix = "mongodb://%s:%s" % (credentials['username'],
                                      credentials['password'])
        self.connection_url = prefix + "@" + connection_url + "/github?authSource=admin"

    def initialize_mongodb(self):
        try:
            # Connecting to MongoDB
            print("\nConnecting to MongoDB...")
            self.mongo_client = MongoClient(self.connection_url)

            # Setting Database
            self.db = self.mongo_client[MONGODB_DB_NAME]
            # Setting Collection
            self.repository_collection = self.db[MONGODB_GITHUB_REPOSITORIES_COLLECTION_NAME]
            self.metadata_collection = self.db[MONGODB_GITHUB_METADATA_COLLECTION_NAME]
            self.branches_collection = self.db[MONGODB_GITHUB_BRANCHES_COLLECTION_NAME]
            self.contributors_collection = self.db[MONGODB_GITHUB_CONTRIBUTORS_COLLECTION_NAME]

            print("\nServer Configurations: {}".format(
                self.mongo_client.server_info()))
            print("\nMongoDB Initialized Successfully!")
        except Exception as err:
            print("\nMongo Exception: ", err)

    def insertRepositoryInitializationDocuments(self):
        repositories_document = {
            "repo_id": "none",
            "repository": "none",
            "full_name": "none",
            "visibility": "none",
            "default_branch": "none",
            "archived": False,
            "disabled": True,
            "has_projects":  False,
            "allow_forking":  False,
            "forks_count": -1,
            "stargazers_count": -1,
            "watchers_count": -1,
            "issues": [{
                "all": -1,
                "open": -1,
                "closed": -1
            }],
            "pulls": [{
                "all": -1,
                "open": -1,
                "closed": -1
            }],
            "branches": [{
                "all": -1,
                "stale": -1,
                "active": -1,
            }],
            "contributors": [{
                "all": -1,
                "user": -1,
                "bot": -1,
                "other": -1,
            }],
            "license": "none",
            "language": "none",
            "created_date": self.today,
            "created_at": datetime.utcnow(),
            "updated_at": datetime.utcnow(),
            "pushed_at": datetime.utcnow(),
            "identifier": GITHUB_REPOSITORY_INDEX_IDENTIFIER
        }

        # Inserting Repositories Document
        try:
            self.create_document(data=repositories_document,
                                 collection=self.repository_collection)
            print("Repositories Document Initialized Successfully!")
        except Exception as err:
            print("Initialization Document Insertion Error: ", err)

    def insertMetadataInitializationDocuments(self):
        metadata_document = {
            "repositories": -1,
            "archived": -1,
            "disabled": -1,
            "forks_count": -1,
            "stargazers_count": -1,
            "watchers_count": -1,
            "allow_forking": -1,
            "issues": [{
                "all": -1,
                "open": -1,
                "closed": -1,
            }],
            "pulls": [{
                "all": -1,
                "open": -1,
                "closed": -1,
            }],
            "branches": [{
                "all": 0,
                "active": 0,
                "stale": 0,
            }],
            "contributors": [{
                "all": 0,
                "user": 0,
                "bot": 0,
                "other": 0,
            }],
            "created_date": self.today,
            "identifier": GITHUB_METADATA_INDEX_IDENTIFIER
        }

        # Inserting Metadata Document
        try:
            self.create_document(data=metadata_document,
                                 collection=self.metadata_collection)
            print("Metadata Collection Initialized Successfully!")
        except Exception as err:
            print("Initialization Document Insertion Error: ", err)

    def insertBranchesInitializationDocuments(self):
        branches_document = {
            "unique_identity": "none:none",
            "branch": "none",
            "repository": "none",
            "protected": False,
            "last_commit": "2015-07-31T21:26:18Z",
            "protection": ["none"],
            "protection_enabled": False,
            "status": "none",
            "created_date": self.today,
            "identifier": GITHUB_BRANCHES_INDEX_IDENTIFIER
        }

        # Inserting Branches Document
        try:
            self.create_document(data=branches_document,
                                 collection=self.branches_collection)
            print("Branches Collection Initialized Successfully!")
        except Exception as err:
            print("Initialization Document Insertion Error: ", err)

    def insertContributorsInitializationDocuments(self):
        contributors_document = {
            "unique_identity": "none:none",
            "repository": "none",
            "contributor": "none",
            "contributions": -1,
            "type": "none",
            "created_date": self.today,
            "identifier": GITHUB_CONTRIBUTORS_INDEX_IDENTIFIER
        }

        # Inserting Contributors Document
        try:
            self.create_document(data=contributors_document,
                                 collection=self.contributors_collection)
            print("Contributors Collection Initialized Successfully!")
        except Exception as err:
            print("Initialization Document Insertion Error: ", err)

    def read_collection(self, collection):
        try:
            print("\nReading Collection: ")
            resp = collection.find({}, {"_id": True})

            if resp is None:
                print("Document Not Found")
                return False

            # If cursor is not empty then return true
            resp = list(resp)
            # print(resp)
            if len(resp) > 0:
                return True

            return False
        except Exception as err:
            print("Read Document Exception: ", err)
            raise

    def create_document(self, data, collection):
        try:
            print("\nCreating MongoDB Document: ")
            resp = collection.insert_one(data)
            print("Response - ", resp.inserted_id)
            self.document_id = resp.inserted_id
        except Exception as err:
            print("Insert Document Exception: ", err)

    def update_document(self, data, collection):
        try:
            print("\nUpdating MongoDB Document: ")
            resp = collection.update_one(
                {"_id": self.document_id}, update={"$set": data})
            print("Response - ", resp, " -> ", resp.acknowledged)
        except Exception as err:
            print("Update Document Exception: ", err)
