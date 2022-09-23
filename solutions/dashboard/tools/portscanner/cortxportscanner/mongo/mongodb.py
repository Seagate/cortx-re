from cortxportscanner.common.const import INDEX_IDENTIFIER, MONGODB_DB_NAME, MONGODB_COLLECTION_NAME
from datetime import datetime
from pymongo import MongoClient


class MongoDB:
    def __init__(self) -> None:
        self.mongo_client = None
        self.db = None
        self.collection = None
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
            self.collection = self.db[MONGODB_COLLECTION_NAME]

            print("\nServer Configurations: {}".format(
                self.mongo_client.server_info()))
            print("\nMongoDB Initialized Successfully!")
        except Exception as err:
            print("\nMongo Exception: ", err)

    def read_collection(self):
        try:
            print("\nReading Collection: ")
            resp = self.collection.find({}, {"_id": True})

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

    def create_document(self, data):
        try:
            print("\nCreating MongoDB Document: ")
            resp = self.collection.insert_one(data)
            print("Response - ", resp.inserted_id)
            self.document_id = resp.inserted_id
        except Exception as err:
            print("Insert Document Exception: ", err)

    def update_document(self, data):
        try:
            print("\nUpdating MongoDB Document: ")
            resp = self.collection.update_one(
                {"_id": self.document_id}, update={"$set": data})
            print("Response - ", resp, " -> ", resp.acknowledged)
        except Exception as err:
            print("Update Document Exception: ", err)

    def manage_document(
            self, actual_ports: list,
            allowed_ports: list,
            non_compliance_ports: list,
            non_compliance_services: list,
            is_healthy: bool,
            is_healthy_int: int):
        try:
            data = {
                "identifier": INDEX_IDENTIFIER,
                "actual_ports": actual_ports,
                "allowed_ports": allowed_ports,
                "non_compliance_ports": non_compliance_ports,
                "non_compliance_services": non_compliance_services,
                "is_healthy": is_healthy,
                "is_healthy_int": is_healthy_int,
            }

            data["created_at"] = datetime.now()
            self.create_document(data=data)

        except Exception as err:
            print("Manage Document Exception: ", err)
