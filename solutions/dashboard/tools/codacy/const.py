HEADERS = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
}

PROVIDER = "gh"

ORGANIZATION = "Seagate"

NAMESPACE = "cortx"

# MongoDB
MONGODB_DB_NAME = "codacy"
MONGODB_CODACY_REPOSITORIES_COLLECTION_NAME = "repositories"
MONGODB_CODACY_METADATA_COLLECTION_NAME = "metadata"
MONGODB_PORT = 27017
MONGODB_CONNECTION_URL = "cortx-port-scanner-mongodb.cortx.svc.cluster.local:27017/codacy?authSource=admin"

# Logstash
CODACY_REPOSITORY_INDEX_IDENTIFIER = "codacy.repositories"
CODACY_METADATA_INDEX_IDENTIFIER = "codacy.metadata"
