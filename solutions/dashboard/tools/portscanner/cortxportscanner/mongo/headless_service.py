from cortxportscanner.common.const import HEADLESS_SERVICE_NAME, STATEFULSET_NAME
from kubernetes import client
class HeadlessService:
    def __init__(self, client: client, core_api: client.CoreV1Api, specs):
        self.client = client
        self.core_api = core_api
        self.specs = specs

    def handle_headless_service(self):
        self.create_headless_service()

    def create_headless_service(self):
        headless_service_body = self.create_headless_service_body()
        
        try:
            resp = self.core_api.create_namespaced_service(namespace=self.specs['namespace'], body=headless_service_body)
            print("\n\nHeadless Service Created")
            # print(resp)
        except Exception as err:
            print("Exception: ", err)
            
    def create_headless_service_body(self):
        metadata = self.client.V1ObjectMeta(
            name=HEADLESS_SERVICE_NAME,
            namespace=self.specs['namespace']
        )

        specs=self.client.V1ServiceSpec(
            selector={"name": STATEFULSET_NAME},
            cluster_ip="None",
            type="ClusterIP",
            ports=[self.client.V1ServicePort(
                port=27017,
                target_port=27017,
                protocol="TCP"
            )]
        )

        body = self.client.V1Service(
            api_version="v1",
            kind="Service",
            metadata=metadata,
            spec=specs
        )
        
        return body