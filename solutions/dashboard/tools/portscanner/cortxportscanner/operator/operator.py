import asyncio
from functools import partial
import kubernetes
from kubernetes import client
from kubernetes.client.exceptions import ApiException
from cortxportscanner.mongo.mongodb import MongoDB
from cortxportscanner.common.const import ALLOWED_EVENT_TYPES, LIST_TYPES_MAP
from cortxportscanner.operator.configmap import ConfigMap


class Operator:
    def __init__(self, client: client, core_api: client.CoreV1Api, specs: dict, configmap: ConfigMap, mongodb: MongoDB):
        self.client = client
        self.core_api = core_api
        self.specs = specs
        self.configmap = configmap

        self.service_listening_ports = []
        self.actual_ports = []
        self.allowed_ports = []
        self.non_compliance_ports = []
        self.non_compliance_services = []

        self.service_name = ""
        self.resource_version = ''
        self.fetchList = False
        self.firstIteration = True

        self.mongodb = mongodb

        # Startup documents
        # Create
        self.mongodb.manage_document(
            actual_ports=[-1],
            allowed_ports=[-1],
            non_compliance_ports=[-1],
            non_compliance_services=["none"],
            is_healthy=False,
            is_healthy_int=0)
       
        self.mongodb.manage_document(
            actual_ports=[-1],
            allowed_ports=[-1],
            non_compliance_ports=[-1],
            non_compliance_services=["none"],
            is_healthy=False,
            is_healthy_int=0)

    def handle_operator(self):
        ioloop = asyncio.get_event_loop()
        ioloop.create_task(self.track_services())
        ioloop.run_forever()

    async def track_services(self):
        w = kubernetes.watch.Watch()

        # Get the method to watch the objects
        method = getattr(
            self.core_api, LIST_TYPES_MAP[self.specs['scanObject']])
        func = partial(method, self.specs['namespace'])

        while True:

            print("\n--------------------------------------------------------")
            self.resource_version = self.configmap.handle_configmaps(
                firstIteration=self.firstIteration,
                current_allowed_ports=self.allowed_ports,
                resource_version=self.resource_version)
            print("\n--------------------------------------------------------\n\n")
            self.firstIteration = False

            try:
                if self.fetchList:
                    # As we haven't passed resource_version
                    # it will always give most recent resource version
                    res = self.core_api.list_namespaced_service(
                        namespace=self.specs['namespace'])
                    print("Response: {}".format(res.metadata))
                    self.resource_version = res.metadata.resource_version
                    self.fetchList = False

                # In case if resouce_version is '' [blank]
                # Then watch will receive all events again
                # Then we must have to clear all actual_ports
                if (self.resource_version == ''):
                    self.actual_ports.clear()

                print("\n>>> Events Listening Started, Resource Version: {}".format(
                    self.resource_version))

                # Using resource_version to watch events after that resource version
                for event in w.stream(func, _request_timeout=60, timeout_seconds=3600, resource_version=self.resource_version):
                    self.resource_version = event['object']['metadata']['resourceVersion']
                    self.handle_event(event=event)

            except ApiException as err:
                # Resource too old
                print("--> API Exception: {}".format(err))

                if err.status == 410:
                    self.resource_version = ''
                    self.fetchList = True

            except Exception as err:
                # print("\nResource Version: {}".format(resource_version), end=", ")
                print("\n--> Exception: {}\n".format(err))
                print("\n---")
                print("---\n")

    def handle_event(self, event):
        print("\n\nGot Event: ")
        print("Event Type: {}".format(event["type"]))
        print("Service Name: {}".format(event['object']["metadata"]["name"]))
        self.service_name = event['object']["metadata"]["name"]

        # The method for processing one Kubernetes event.

        if event['type'] not in ALLOWED_EVENT_TYPES:
            print("Handling Status: Not in allowed type")
            print("Event: {}".format(event))
            print("Handling Status: COMPLETED")
            return

        object_ = event['object']

        print("Handling Status: INPROGRESS")

        print("Service Configurations: ")
        print("Cluster IP: {}".format(object_["spec"]["clusterIP"]))

        if event['type'] == "DELETED":
            print("Service was Listening on Ports: ")
        else:
            print("Service Listening on Ports: ")

        if object_["spec"]["clusterIP"] != "None":
            service_ports = object_["spec"]["ports"]
            self.extract_ports(event=event, service_ports=service_ports)

            self.compare_ports(event=event)

        print("{} Handling Status: COMPLETED \n\n".format(
            object_["metadata"]["name"]))

    def extract_ports(self, event, service_ports):
        for port_item in service_ports:
            print("- {}".format(port_item))

            # If port is there then add it to actual_ports
            if "port" in port_item:
                if port_item["port"] != "NoneType":
                    self.service_listening_ports.append(int(port_item["port"]))

            # getting targetPort
            if "targetPort" in port_item:
                if port_item["targetPort"] != "NoneType":
                    # Check whether digit present or not
                    if any(char.isdigit() for char in str(port_item["targetPort"])):
                        self.service_listening_ports.append(
                            int(port_item["targetPort"]))

            # If nodePort key is present in port_item dictionary then continue
            if "nodePort" in port_item:
                if port_item["nodePort"] is not None:
                    # Check if node port is >= 30000 and <=32767
                    if int(port_item["nodePort"]) < 30000 or int(port_item["nodePort"]) > 32767:
                        self.service_listening_ports.append(
                            int(port_item["nodePort"]))

    def compare_ports(self, event):

        # Find whether service is non-compliance or not
        try:
            if event['type'] == "ADDED":
                self.actual_ports.extend(self.service_listening_ports)

                # Checking non-compliance for single service
                res = list(set(self.service_listening_ports) -
                           set(self.allowed_ports))
                if len(res) > 0:
                    self.non_compliance_services.append(self.service_name)

            elif event['type'] == "DELETED":
                for item in self.service_listening_ports:
                    self.actual_ports.remove(item)

                # Checking if service present in non_compliance_services or not
                if self.service_name in self.non_compliance_services:
                    self.non_compliance_services.remove(self.service_name)

            self.non_compliance_services.sort()

        except Exception as err:
            print("Exception while service non-compliance check: ", err)

        self.service_listening_ports.clear()

        # Ports Comparison
        self.actual_ports.sort()
        print("Actual Ports: \n{}".format(self.actual_ports))

        unique_ports = list(set(self.actual_ports))
        unique_ports.sort()
        print("\nUnique Actual Ports: \n{}".format(unique_ports))

        print("\nAllowed Ports: \n{}\n".format(self.allowed_ports))

        self.non_compliance_ports = list(
            set(self.actual_ports) - set(self.allowed_ports))
        self.non_compliance_ports.sort()
        print("Non-Compliance Ports: \n{}\n".format(self.non_compliance_ports))

        print("Non-Compliance Services: \n{}\n".format(self.non_compliance_services))

        # Update MongoDB document
        is_healthy = True
        is_healthy_int = 1

        if len(self.non_compliance_ports) > 0:
            is_healthy = False
            is_healthy_int = 0

        self.mongodb.manage_document(
            actual_ports=self.actual_ports,
            allowed_ports=self.allowed_ports,
            non_compliance_ports=self.non_compliance_ports,
            non_compliance_services=self.non_compliance_services,
            is_healthy=is_healthy,
            is_healthy_int=is_healthy_int)
