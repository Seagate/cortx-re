# CRD Settings
CRD_GROUP = 'seagate.com'
CRD_VERSION = 'v1'
CRD_PLURAL = 'cortxportscanners'

# Type methods maps
LIST_TYPES_MAP = {
    'service': 'list_namespaced_service',
    'configmap': 'list_namespaced_config_map'
}

# Allowed events
ALLOWED_EVENT_TYPES = {'ADDED', 'DELETED'}

# Secret
SECRET_NAME = "dashboard-secret"

# ConfigMap
CONFIGMAP_NAME = "cortx-port-scanner"

# Headless Service
HEADLESS_SERVICE_NAME = "cortx-port-scanner-mongodb"

# Statefulset 
STATEFULSET_NAME = "cortx-port-scanner-mongodb"
STATEFULSET_VOLUME_NAME = "data" # Pod name will append to this

# MongoDB
MONGODB_DB_NAME = "cortxportscanner"
MONGODB_COLLECTION_NAME = "operator"
MONGODB_PORT = 27017
MONGODB_CONNECTION_URL = "cortx-port-scanner-mongodb.cortx.svc.cluster.local:" + str(MONGODB_PORT) + "/" + MONGODB_DB_NAME + "?authSource=admin"

# Logstash
INDEX_IDENTIFIER = "cortxportscanner.operator"