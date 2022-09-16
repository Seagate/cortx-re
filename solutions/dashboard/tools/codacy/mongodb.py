from const import CODACY_METADATA_INDEX_IDENTIFIER, CODACY_REPOSITORY_INDEX_IDENTIFIER, MONGODB_DB_NAME
from const import MONGODB_CODACY_REPOSITORIES_COLLECTION_NAME, MONGODB_CODACY_METADATA_COLLECTION_NAME
from pymongo import MongoClient
from datetime import date, datetime


class MongoDB:
    def __init__(self) -> None:
        self.mongo_client = None
        self.db = None
        self.repository_collection = None
        self.metadata_collection = None
        self.document_id = None

        self.connection_url = None

    def handle_mongodb(self):
        self.initialize_mongodb()

    def create_connection_url(self, credentials: dict, connection_url):
        prefix = "mongodb://%s:%s" % (credentials['username'],
                                      credentials['password'])
        self.connection_url = prefix + "@" + connection_url

    def initialize_mongodb(self):
        try:
            # Connecting to MongoDB
            print("\nConnecting to MongoDB...")
            self.mongo_client = MongoClient(self.connection_url)

            # Setting Database
            self.db = self.mongo_client[MONGODB_DB_NAME]
            # Setting Collection
            self.repository_collection = self.db[MONGODB_CODACY_REPOSITORIES_COLLECTION_NAME]
            self.metadata_collection = self.db[MONGODB_CODACY_METADATA_COLLECTION_NAME]

            print("\nServer Configurations: {}".format(
                self.mongo_client.server_info()))
            print("\nMongoDB Initialized Successfully!")
        except Exception as err:
            print("\nMongo Exception: ", err)

    def insertInitializationDocuments(self):
        today = date.today()
        today = today.strftime("%d-%b-%Y")

        metadata_document = {
            "all_categories": [{
                "total_issues": -1
            }],
            "security": [{
                "total_issues": -1,
                "minor_issues": -1,
                "major_issues": -1,
                "critical_issues": -1,
            }],
            "codacy_link": "none",
            "identifier": CODACY_METADATA_INDEX_IDENTIFIER,
            "created_date": today,
            "created_at": datetime.now(),
        }

        repositories_document = {
            "repository": "none",
            "categories": [{
                "none": -1
            }],
            "all_categories": [{
                "total_issues": -1
            }],
            "security": [{
                "issues": -1,
                "minor": -1,
                "medium": -1,
                "critical": -1
            }],
            "codacy_repository_link": "none",
            "identifier": CODACY_REPOSITORY_INDEX_IDENTIFIER,
            "created_date": today,
            "created_at": datetime.now(),
        }

        # Inserting Metadata Document
        try:
            self.create_document(data=metadata_document,
                                 collection=self.metadata_collection)
        except Exception as err:
            print("Initialization Document Insertion Error: ", err)

        # Inserting Repositories Document
        try:
            self.create_document(data=repositories_document,
                                 collection=self.repository_collection)
        except Exception as err:
            print("Initialization Document Insertion Error: ", err)

    def read_collection(self, collection):
        try:
            print("\nReading Collection: ")
            resp = collection.find({}, {"_id": True})

            if resp is None:
                print("Document Not Found")
                return False

            for record in resp:
                print("Record -> {}".format(record))
                self.document_id = record["_id"]
                return True

            print("Document Not Found")
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
