from kubernetes import client
from dashboardportscanner.common.const import CRD_GROUP, CRD_VERSION, CRD_PLURAL


class CR:
    def __init__(self, client: client, namespace, cr_name):
        self.client = client
        self.namespace = namespace
        self.cr_name = cr_name

    def load_cr(self):
        """
        Method for CRD loading.
        It is used to get the object's watching settings.
        """

        print("\n\nFetching CR Object: ")
        client = self.client.ApiClient()
        custom_api = self.client.CustomObjectsApi(client)
        try:
            cr = custom_api.get_namespaced_custom_object(
                group=CRD_GROUP,
                version=CRD_VERSION,
                namespace=self.namespace,
                plural=CRD_PLURAL,
                name=self.cr_name,
            )
            return {
                "namespace": cr['spec']['namespace'],
                "scanObject": cr['spec']['scanObject'],
                "allowedPorts": cr['spec']['allowedPorts']}
        except Exception as err:
            print("Exception while fetching CR: {}".format(err))
