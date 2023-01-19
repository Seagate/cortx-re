from const import JENKINS_JOBS_INDEX_IDENTIFIER, MONGODB_DB_NAME
from const import JENKINS_NODES_INDEX_IDENTIFIER, JENKINS_BUILDS_INDEX_IDENTIFIER, JENKINS_PLUGINS_INDEX_IDENTIFIER
from const import MONGODB_JENKINS_JOBS_COLLECTION_NAME, MONGODB_JENKINS_NODES_COLLECTION_NAME
from const import MONGODB_JENKINS_PLUGINS_COLLECTION_NAME, MONGODB_JENKINS_BUILDS_COLLECTION_NAME
from pymongo import MongoClient


class MongoDB:
    def __init__(self, today) -> None:
        self.mongo_client = None
        self.today = today
        self.db = None
        self.jenkins_jobs_collection = None
        self.jenkins_nodes_collection = None
        self.jenkins_plugins_collection = None
        self.jenkins_builds_collection = None
        self.document_id = None

        self.connection_url = None

    def handle_mongodb(self):
        self.initialize_mongodb()

    def create_connection_url(self, credentials: dict, connection_url):
        prefix = "mongodb://%s:%s" % (credentials['username'],
                                      credentials['password'])
        self.connection_url = prefix + "@" + connection_url + "/jenkins?authSource=admin"

    def initialize_mongodb(self):
        try:
            # Connecting to MongoDB
            print("\nConnecting to MongoDB...")
            self.mongo_client = MongoClient(self.connection_url)

            # Setting Database
            self.db = self.mongo_client[MONGODB_DB_NAME]
            # Setting Collection
            self.jenkins_jobs_collection = self.db[MONGODB_JENKINS_JOBS_COLLECTION_NAME]
            self.jenkins_nodes_collection = self.db[MONGODB_JENKINS_NODES_COLLECTION_NAME]
            self.jenkins_plugins_collection = self.db[MONGODB_JENKINS_PLUGINS_COLLECTION_NAME]
            self.jenkins_builds_collection = self.db[MONGODB_JENKINS_BUILDS_COLLECTION_NAME]

            print("\nServer Configurations: {}".format(
                self.mongo_client.server_info()))
            print("\nMongoDB Initialized Successfully!")
        except Exception as err:
            print("\nMongo Exception: ", err)

    def insertJobsInitializationDocuments(self):
        jobs_document = {
            "_class": "none",
            "jobname": "none",
            "jobfullname": "none",
            "joburl": "none",
            "jobdescription": "none",
            "buildable": True,
            "color": "none",
            "inQueue": False,
            "queueItem": None,
            "concurrentBuild": False,
            "keepDependencies": False,
            "disabled": False,
            "labelExpression": "none",
            "firstBuild": 5,
            "lastBuild": 13,
            "lastCompletedBuild": 13,
            "lastStableBuild": 13,
            "lastSuccessfulBuild": 13,
            "nextBuildNumber": 14,
            "projectUrl": "none",
            "jobactions": [
                "none",
            ],
            "jobproperty": ["none"],
            "scm": [{"url": "none", "branch": "none"}],
            "TimerTrigger": [{"scheduled": True, "schedule": "none"}],
            "SCMTrigger": [{"spec": "none", "ignorePostCommitHooks": "false"}],
            "triggers": [
                "none",
            ],
            "activeConfigurations": [],
            "jobparameters": ["none"],
            "downstreamProjects": ["none"],
            "upstreamProjects": ["none"],
            "primaryView": "none",
            "views": ["none"],
            "healthScore": 20,
            "healthDescription": "none",
            "category": "none",
            "isOlder": "none",
            "nodename": "none",
            "labelname": "none",
            "created_date": self.today,
            "identifier": JENKINS_JOBS_INDEX_IDENTIFIER
        }

        # Inserting Repositories Document
        try:
            self.create_document(data=jobs_document,
                                 collection=self.jenkins_jobs_collection)
        except Exception as err:
            print("Initialization Document Insertion Error: ", err)

    def insertNodesInitializationDocuments(self):
        nodes_document = {
            "nodename": "none",
            "nodedescription": "none",
            "nodedisplayName": "none",
            "offline": False,
            "idle": True,
            "jnlpAgent": False,
            "launchSupported": True,
            "manualLaunchAllowed": True,
            "launchStatistics": "none",
            "numExecutors": 2,
            "offlineCauseReason": "none",
            "temporarilyOffline": False,
            "absoluteRemotePath": "none",
            "totalPhysicalMemory": -1,
            "availablePhysicalMemory": -1,
            "totalSwapSpace": -1,
            "availableSwapSpace": -1,
            "temporarySpaceSize": -1,
            "temporarySpacePath": "none",
            "diskSpaceSize": -1,
            "diskSpacePath": "none",
            "architectureMonitor": "none",
            "responseTimeAverage": -1,
            "clockDiff": -1,
            "labelname": ["none"],
            "nodehostname": "none",
            "nodeport": "none",
            "nodeProperties": [
                {"count": "1", "environmentVariables": [
                    {"name": "ENV", "value": "DEV"}]},
            ],
            "created_date": self.today,
            "identifier": JENKINS_NODES_INDEX_IDENTIFIER
        }

        # Inserting Repositories Document
        try:
            self.create_document(data=nodes_document,
                                 collection=self.jenkins_nodes_collection)
        except Exception as err:
            print("Initialization Document Insertion Error: ", err)

    def insertPluginsInitializationDocuments(self):
        plugins_document = {
            "longName": "none",
            "shortName": "none",
            "active": True,
            "backupVersion": None,
            "bundled": False,
            "deleted": False,
            "dependencies": [
                {"optional": True, "shortName": "none", "version": "none"},
            ],
            "detached": True,
            "downgradable": False,
            "enabled": True,
            "hasUpdate": False,
            "pinned": False,
            "requiredCoreVersion": "none",
            "supportsDynamicLoad": "none",
            "url": "none",
            "version": "none",
            "created_date": self.today,
            "identifier": JENKINS_PLUGINS_INDEX_IDENTIFIER
        }

        # Inserting Repositories Document
        try:
            self.create_document(data=plugins_document,
                                 collection=self.jenkins_plugins_collection)
        except Exception as err:
            print("Initialization Document Insertion Error: ", err)

    def insertBuildsInitializationDocuments(self):
        builds_document = {
            "jobname": "none",
            "jobfullname": "none",
            "building": False,
            "builddisplayName": "none",
            "duration": -1,
            "estimatedDuration": -1,
            "buildfullDisplayName": "none",
            "keepLog": False,
            "number": -1,
            "queueId": -1,
            "result": "none",
            "build_timestamp": self.today,
            "buildurl": "none",
            "executor": None,
            "labelname": "none",
            "causes": [
                {
                    "_class": "none",
                    "shortDescription": "none",
                    "upstreamBuild": -1,
                    "upstreamProject": "none",
                    "upstreamUrl": "none",
                    "userId": "none",
                    "userName": "none",
                },
            ],
            "timeInQueue": [
                {
                    "_class": "none",
                    "blockedDurationMillis": -1,
                    "blockedTimeMillis": -1,
                    "buildableDurationMillis": -1,
                    "buildableTimeMillis": -1,
                    "buildingDurationMillis": -1,
                    "executingTimeMillis": -1,
                    "executorUtilization": -1,
                    "subTaskCount": -1,
                    "waitingDurationMillis": -1,
                    "waitingTimeMillis": -1,
                },
            ],
            "buildactions": [
                "none",
            ],
            "unique_build_name": "none",
            "category": "none",
            "created_date": self.today,
            "identifier": JENKINS_BUILDS_INDEX_IDENTIFIER
        }

        # Inserting Repositories Document
        try:
            self.create_document(data=builds_document,
                                 collection=self.jenkins_builds_collection)
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
