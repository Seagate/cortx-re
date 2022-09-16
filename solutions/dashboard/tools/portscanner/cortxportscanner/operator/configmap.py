from kubernetes import client

from cortxportscanner.common.const import CONFIGMAP_NAME


class ConfigMap:
    def __init__(self, client: client, core_api: client.CoreV1Api, specs):
        self.client = client
        self.core_api = core_api
        self.specs = specs

    def handle_configmaps(self, firstIteration, current_allowed_ports, resource_version):
        print("\n\nHandling ConfigMaps: ")

        # Read ConfigMap
        configmap = self.read_configmap()

        # If None means ConfigMap not exists
        if (configmap is None):
            print("ConfigMap Not Found...Creating new one")

            # Check whether it is first iteration or not

            # If it is first iteration take ports from CR and create ConfigMap
            # if not then there might be the case that ConfigMap updated and current_allowed_ports have
            # updated ports than CR specification. So, create ConfigMap using these ports

            if firstIteration:
                print("\nPorts:\nTaking ports from CR specifications")
                self.create_configmap()
                current_allowed_ports.clear()
                current_allowed_ports.extend(self.specs['allowedPorts'])
            else:
                self.create_configmap(
                    current_allowed_ports=current_allowed_ports)

        else:
            print("ConfigMap Found")

            # Extracting ports from configmap
            try:
                configmap_ports_str = configmap.data['allowedPorts']
                print("ConfigMap Data: ", configmap_ports_str)
                configmap_ports = list(
                    map(int, configmap_ports_str.split(",")))

                # If it is first iteration just take ports from configmap
                if firstIteration:
                    print("\nPorts:\nTaking ports from ConfigMap")
                    current_allowed_ports.clear()
                    current_allowed_ports.extend(configmap_ports)
                else:
                    # Checking whether configmap is modified or not
                    # If it is modified then we need to process all the services from beginning
                    configmap_ports.sort()
                    current_allowed_ports.sort()

                    if configmap_ports != current_allowed_ports:
                        print("Difference found in ports...Getting new ports")
                        print("Starting service processing from beginning")
                        current_allowed_ports.clear()
                        current_allowed_ports.extend(configmap_ports)
                        resource_version = ''
                    else:
                        print("Ports are same...Continue")
                        print("No need to start from beginning")

            except Exception as err:
                print("Exception while extracting ports from configmap: ", err)
                if firstIteration:
                    print("Taking ports from CR Specification", err)
                    current_allowed_ports.clear()
                    current_allowed_ports.extend(self.specs['allowedPorts'])

        return resource_version

    def create_configmap(self, current_allowed_ports=None):
        configmap = self.create_configmap_object(
            current_allowed_ports=current_allowed_ports)

        try:
            self.core_api.create_namespaced_config_map(
                namespace=self.specs['namespace'], body=configmap)
        except Exception as err:
            print("Exception while creating ConfigMap: {}".format(err))

    def create_configmap_object(self, current_allowed_ports):
        # current_allowed_ports None means it is first iteration
        # So use ports from specifications
        if current_allowed_ports is None:
            # Creating new string of allowed ports
            print("Using CR Ports")
            allowedPorts = self.specs['allowedPorts']
        else:
            print("Using Old Ports")
            allowedPorts = current_allowed_ports

        allowedPorts = ','.join(map(str, allowedPorts))

        print("Allowed specs ports adding to ConfigMap: ",
              self.specs['allowedPorts'])
        print("Allowed Ports adding to ConfigMap: ", allowedPorts)
        metadata = self.client.V1ObjectMeta(
            name=CONFIGMAP_NAME,
            namespace=self.specs['namespace'],
        )
        # Instantiate the configmap object
        configmap = self.client.V1ConfigMap(
            api_version="v1",
            kind="ConfigMap",
            data=dict(
                allowedPorts=allowedPorts,
                connectionUrl=self.specs['connectionUrl']),
            metadata=metadata
        )

        return configmap

    def read_configmap(self):
        configmap = None

        try:
            configmap = self.core_api.read_namespaced_config_map(
                name=CONFIGMAP_NAME, namespace=self.specs['namespace'])
        except Exception as err:
            print("Exception while reading ConfigMap: ", err)

        return configmap
