import os
from kubernetes import client, config
from kubernetes import client

from cortxportscanner.mongo.headless_service import HeadlessService
from cortxportscanner.mongo.mongodb import MongoDB
from cortxportscanner.mongo.statefulset import StatefulSet
from cortxportscanner.operator.configmap import ConfigMap
from cortxportscanner.operator.cr import CR
from cortxportscanner.operator.operator import Operator

class Main:
    def __init__(self, args):
        self.args = args

    def load_kubernetes(self):
        try:
            self.kubernetes = config.load_incluster_config()
            self.core_api = client.CoreV1Api()
            self.apps_api = client.AppsV1Api()
        except config.config_exception.ConfigException:
            raise RuntimeError(
                'Can not read Kubernetes cluster configuration.'
            )

    def main(self):
        # Base64
        credentials = {
            "username": os.environ.get("MONGODB_USERNAME"),
            "password": os.environ.get("MONGODB_PASSWORD")
        } 

        try:
            print("\n\n==============================================================")
            print("\tModule Started....Performing Initial Configurations")
            print("==============================================================\n")
            
            print("Got Arguments: ")
            print("Namespace = {}, CR = {}".format(self.args.namespace, self.args.cr_name))
            
            # Getting CR Specifications
            cr = CR(client=client, namespace=self.args.namespace, cr_name=self.args.cr_name)
            specs = cr.load_cr()
            print("CR Specifications: {}".format(specs))

            # Handle Configmap [For Connection URL Only]
            print("\n\n-----\nHandling ConfigMap: ")
            configmap = ConfigMap(
                client=client, 
                core_api=self.core_api, 
                specs=specs)
            configmap_res = configmap.read_configmap()
            
            mongodb_connection_url = None
            if configmap_res == None:
                print("ConfigMap Not Found...Getting MongoDB connection URL from CR specifications")
                mongodb_connection_url = specs['connectionUrl']
            else:
                print("ConfigMap Found...Getting MongoDB connection URL from ConfigMap")
                mongodb_connection_url = configmap_res.data['connectionUrl']

            # Create Headless Service
            print("\n\n-----\nHandling Headless Service: ")
            headless_service = HeadlessService(
                client=client, 
                core_api=self.core_api,
                specs=specs)
            headless_service.handle_headless_service()
            
            # Create Statefulset
            print("\n\n-----\nHandling Statefuleset: ")
            statefulset = StatefulSet(
                client=client, 
                apps_api=self.apps_api,
                specs=specs)
            statefulset.handle_statefulset()

            # MongoDB Initialization
            print("\n\n-----\nHandling MongoDB: ")
            mongodb = MongoDB()
            mongodb.create_connection_url(credentials=credentials, connection_url=mongodb_connection_url)
            mongodb.handle_mongodb()
            
            # Starting the operator
            operator = Operator(
                client=client, 
                core_api=self.core_api, 
                specs=specs, 
                configmap=configmap, 
                mongodb=mongodb)
            operator.handle_operator()

        except KeyboardInterrupt:
            pass

        except Exception as err:
            raise RuntimeError('Failing...{}'.format(err)) from err