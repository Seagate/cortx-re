from jenkins_core import JenkinsCore
from mongodb import MongoDB
from const import JENKINS_NODES_INDEX_IDENTIFIER
import xmltodict


class JenkinsNode:
    def __init__(self, today, jenkins_core_obj: JenkinsCore, mongodb: MongoDB) -> None:
        self.today = today
        self.jenkins_core_obj = jenkins_core_obj
        self.mongodb = mongodb
        self.nodes = []

    def get_nodes(self):
        try:
            all_nodes = self.jenkins_core_obj.server.get_nodes()

            i = 1
            # Iterate through the nodes
            for node in all_nodes:
                print(str(i) + "=> Node:", node)
                filtered_data = {
                    "nodename": node["name"],
                    "offline": node["offline"]
                }

                if node["name"] == "Built-In Node":
                    continue

                try:
                    node_details = self.jenkins_core_obj.server.get_node_info(
                        name=node["name"])
                    print("Details:", node_details)

                    filtered_node_details = self.get_node_details(
                        node_data=node_details)
                    filtered_data.update(filtered_node_details)

                    node_config = self.jenkins_core_obj.server.get_node_config(
                        name=node["name"]
                    )

                    filtered_node_config = self.get_node_config(
                        node_config_data=node_config)
                    filtered_data.update(filtered_node_config)

                    filtered_data["created_date"] = self.today
                    filtered_data["identifier"] = JENKINS_NODES_INDEX_IDENTIFIER
                    self.nodes.append(filtered_data)

                    self.mongodb.create_document(
                        filtered_data, self.mongodb.jenkins_nodes_collection)

                except Exception as err:
                    print("Exception: Get Node Details ", err)

                i += 1

        except Exception as err:
            print("Exception: Get Nodes ", err)

    def get_node_details(self, node_data: dict):
        filtered_data = {
            "nodedescription": node_data["description"],
            "nodedisplayName": node_data["displayName"],
            "idle": node_data["idle"],
            "jnlpAgent": node_data["jnlpAgent"],
            "launchSupported": node_data["launchSupported"],
            "manualLaunchAllowed": node_data["manualLaunchAllowed"],
            "launchStatistics": node_data["loadStatistics"]["_class"],
            "numExecutors": node_data["numExecutors"],
            "offline": node_data["offline"],
            "offlineCauseReason": node_data["offlineCauseReason"],
            "temporarilyOffline": node_data["temporarilyOffline"],
        }

        if node_data["absoluteRemotePath"] is not None:
            filtered_data["absoluteRemotePath"] = node_data["absoluteRemotePath"]

        # Actions
        if len(node_data["actions"]) > 0:
            actions_data = self.get_actions(data=node_data)
            filtered_data.update(actions_data)

        # Monitor Data
        if "hudson.node_monitors.SwapSpaceMonitor" in node_data["monitorData"]:
            if node_data["monitorData"]["hudson.node_monitors.SwapSpaceMonitor"] is not None:
                swap_space_monitor = node_data["monitorData"]["hudson.node_monitors.SwapSpaceMonitor"]

                filtered_data["totalPhysicalMemory"] = swap_space_monitor["totalPhysicalMemory"]
                filtered_data["availablePhysicalMemory"] = swap_space_monitor["availablePhysicalMemory"]
                filtered_data["totalSwapSpace"] = swap_space_monitor["totalSwapSpace"]
                filtered_data["availableSwapSpace"] = swap_space_monitor["availableSwapSpace"]

        if "hudson.node_monitors.TemporarySpaceMonitor" in node_data["monitorData"]:
            if node_data["monitorData"]["hudson.node_monitors.TemporarySpaceMonitor"] is not None:
                temporary_space_monitor = node_data["monitorData"]["hudson.node_monitors.TemporarySpaceMonitor"]

                filtered_data["temporarySpaceSize"] = temporary_space_monitor["size"]
                filtered_data["temporarySpacePath"] = temporary_space_monitor["path"]

        if "hudson.node_monitors.DiskSpaceMonitor" in node_data["monitorData"]:
            if node_data["monitorData"]["hudson.node_monitors.DiskSpaceMonitor"] is not None:
                disk_space_monitor = node_data["monitorData"]["hudson.node_monitors.DiskSpaceMonitor"]

                filtered_data["diskSpaceSize"] = disk_space_monitor["size"]
                filtered_data["diskSpacePath"] = disk_space_monitor["path"]

        if "hudson.node_monitors.ArchitectureMonitor" in node_data["monitorData"]:
            if node_data["monitorData"]["hudson.node_monitors.ArchitectureMonitor"] is not None:
                filtered_data["architectureMonitor"] = node_data["monitorData"]["hudson.node_monitors.ArchitectureMonitor"]

        if "hudson.node_monitors.ResponseTimeMonitor" in node_data["monitorData"]:
            if node_data["monitorData"]["hudson.node_monitors.ResponseTimeMonitor"] is not None:
                response_time_monitor = node_data["monitorData"]["hudson.node_monitors.ResponseTimeMonitor"]

                filtered_data["responseTimeAverage"] = response_time_monitor["average"]

        if "hudson.node_monitors.ClockMonitor" in node_data["monitorData"]:
            if node_data["monitorData"]["hudson.node_monitors.ClockMonitor"] is not None:
                clock_monitor = node_data["monitorData"]["hudson.node_monitors.ClockMonitor"]

                filtered_data["clockDiff"] = clock_monitor["diff"]

        # Offline Cause
        if node_data["offlineCause"] is not None:
            filtered_data["offlineCause"] = node_data["offlineCause"]

        # Assigned Labels
        if len(node_data["assignedLabels"]) > 0:
            assigned_labels_list = []
            for label in node_data["assignedLabels"]:
                assigned_labels_list.append(label["name"])
            filtered_data["labelname"] = assigned_labels_list

        return filtered_data

    def get_node_config(self, node_config_data):
        filtered_data = {}

        json_node_config = xmltodict.parse(node_config_data)
        print("Config:", dict(json_node_config))

        # Getting host and port
        if ("launcher" in json_node_config["slave"] and "host" in json_node_config["slave"]["launcher"]):
            filtered_data["nodehostname"] = json_node_config["slave"]["launcher"]["host"]

        if ("launcher" in json_node_config["slave"] and "port" in json_node_config["slave"]["launcher"]):
            filtered_data["nodeport"] = json_node_config["slave"]["launcher"]["port"]

        # Getting properties
        if "nodeProperties" in json_node_config["slave"]:
            if json_node_config["slave"]["nodeProperties"] is not None:
                if "hudson.slaves.EnvironmentVariablesNodeProperty" in json_node_config["slave"]["nodeProperties"]:
                    nodePropertiesLocal = json_node_config["slave"]["nodeProperties"][
                        "hudson.slaves.EnvironmentVariablesNodeProperty"]
                    if "envVars" in nodePropertiesLocal:
                        if "tree-map" in nodePropertiesLocal["envVars"]:
                            envs = {}
                            if "int" in nodePropertiesLocal["envVars"]["tree-map"]:
                                envs["count"] = nodePropertiesLocal["envVars"]["tree-map"]["int"]

                            if "string" in nodePropertiesLocal["envVars"]["tree-map"]:
                                envList = nodePropertiesLocal["envVars"]["tree-map"]["string"]
                                extracted_env_items = []
                                i = 0
                                while i < len(envList):
                                    local_env = {
                                        "name": envList[i],
                                        "value": envList[i+1]
                                    }
                                    extracted_env_items.append(local_env)
                                    i += 2
                                envs["environmentVariables"] = extracted_env_items

                                filtered_data["nodeProperties"] = [envs]

        return filtered_data

    def get_actions(self, data: dict):
        filtered_data = {}
        actions_list = []

        for action in data["actions"]:
            if "_class" in action:
                actions_list.append(action["_class"])

        filtered_data["nodeactions"] = actions_list
        return filtered_data
