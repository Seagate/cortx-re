# CRD Settings
CRD_GROUP = 'seagate.com'
CRD_VERSION = 'v1'
CRD_PLURAL = 'dashboardportscanners'

# Type methods maps
LIST_TYPES_MAP = {
    'service': 'list_namespaced_service',
    'configmap': 'list_namespaced_config_map'
}

# Allowed events
ALLOWED_EVENT_TYPES = {'ADDED', 'DELETED'}

# ConfigMap
CONFIGMAP_NAME = "dashboard-port-scanner"

# MongoDB
MONGODB_DB_NAME = "portscanner"
MONGODB_COLLECTION_NAME = "operator"
MONGODB_PORT = 27017

# Logstash
INDEX_IDENTIFIER = "portscanner.operator"
