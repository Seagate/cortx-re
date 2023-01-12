from jenkins_core import JenkinsCore
from mongodb import MongoDB
from const import JENKINS_BUILDS_INDEX_IDENTIFIER
import datetime


class JenkinsBuilds:
    def __init__(self, today, jenkins_core_obj: JenkinsCore, mongodb: MongoDB) -> None:
        self.jenkins_core_obj = jenkins_core_obj
        self.mongodb = mongodb
        self.today = today
        self.builds = []

    def get_builds(self, fullname: str, name: str, builds: list, category: str):
        i = 1

        job_data = {
            "isOlder": "Builds Not Found",
            "nodename": None,
            "labelname": None
        }
        for build in builds:
            print("=> #", build["number"])
            try:
                build_info = self.jenkins_core_obj.server.get_build_info(
                    name=fullname, number=build["number"])

                filtered_build_info = self.get_build_info(
                    build_info=build_info, category=category)

                filtered_build_info["jobname"] = name
                filtered_build_info["jobfullname"] = fullname
                filtered_build_info["unique_build_name"] = fullname + \
                    "#" + str(build["number"])
                filtered_build_info["category"] = category
                filtered_build_info["created_date"] = self.today
                filtered_build_info["identifier"] = JENKINS_BUILDS_INDEX_IDENTIFIER

                self.mongodb.create_document(
                    filtered_build_info, self.mongodb.jenkins_builds_collection)

                self.builds.append(filtered_build_info)

                if i == 1:
                    current_date = datetime.datetime.now()

                    diff = current_date - \
                        filtered_build_info["build_timestamp"]
                    print(diff)

                    if (diff > datetime.timedelta(days=182.5)):
                        print("Older than 6 Months")
                        job_data["isOlder"] = "Yes"
                    else:
                        print("Not older than 6 months")
                        job_data["isOlder"] = "No"

                    # Getting nodename for Workflow Job
                    if category == "WorkflowJob":
                        nodename = self.getBuildLogs(
                            name=fullname, number=build["number"])

                        if nodename is not None:
                            job_data["nodename"] = nodename

                    # Getting node details for FreeStyleProject
                    if category == "FreeStyleProject":
                        envVars = self.getBuildEnv(
                            name=fullname, number=build["number"])

                        job_data["labelname"] = filtered_build_info["labelname"]

                        if envVars is not None:
                            if "envMap" in envVars and envVars["envMap"] is not None:
                                if "NODE_NAME" in envVars["envMap"] and envVars["envMap"]["NODE_NAME"] is not None:
                                    job_data["nodename"] = envVars["envMap"]["NODE_NAME"]

                    if category == "MatrixProject":
                        job_data["labelname"] = filtered_build_info["labelname"]

                if i == 5:
                    break
                i += 1
            except Exception as err:
                print("Exception: Get Build Info, ", err)

        return job_data

    def get_build_info(self, build_info: dict, category: str):
        filtered_data = {
            "building": build_info["building"],
            "builddisplayName": build_info["displayName"],
            "duration": build_info["duration"],
            "estimatedDuration": build_info["estimatedDuration"],
            "buildfullDisplayName": build_info["fullDisplayName"],
            "keepLog": build_info["keepLog"],
            "number": build_info["number"],
            "queueId": build_info["queueId"],
            "result": build_info["result"],
            "build_timestamp": datetime.datetime.utcfromtimestamp(build_info["timestamp"]/1000.0),
            "buildurl": build_info["url"],
            "executor": build_info["executor"],
        }

        if (category != "WorkflowJob"):
            filtered_data["labelname"] = build_info["builtOn"]

        # Description Information
        if "description" in build_info and build_info["description"] is not None:
            filtered_data["builddescription"] = build_info["description"]

        # Taking actions
        if "actions" in build_info and len(build_info["actions"]) > 0:
            actions_list = []

            for action in build_info["actions"]:
                if "_class" in action:
                    actions_list.append(action["_class"])

                    # Take started by
                    if action["_class"] == "hudson.model.CauseAction":
                        if len(action["causes"]) > 0:
                            filtered_data["causes"] = action["causes"]

                    # Take TimeInQueue
                    if action["_class"] == "jenkins.metrics.impl.TimeInQueueAction":
                        filtered_data["timeInQueue"] = [action]

                    # Build Data
                    if action["_class"] == "hudson.plugins.git.util.BuildData":
                        if "remoteUrls" in action and action["remoteUrls"] is not None and len(action["remoteUrls"]) > 0:
                            filtered_data["remoteUrls"] = action["remoteUrls"]

            filtered_data["buildactions"] = actions_list

        return filtered_data

    def getBuildLogs(self, name, number):
        consoleOutput = self.jenkins_core_obj.server.get_build_console_output(
            name=name, number=number)

        consoleOutput = consoleOutput.split('\n')

        for line in consoleOutput:
            # print(i, " => ", line)
            res = line.split(" ")
            print(res)

            if res is not None and len(res) > 0:
                if res[0] == "Running" and res[1] == "on":
                    print("Found: ", res[2])
                    return res[2]
                elif res[0] == "Building" and res[1] == "on" and res[2] == "the":
                    print("Found: ", res[3])
                    return res[3]
                elif res[0] == "Building" and res[1] == "remotely" and res[2] == "on":
                    print("Found: ", res[3])
                    return res[4]
        return None

    def getBuildEnv(self, name, number):
        envVar = self.jenkins_core_obj.server.get_build_env_vars(
            name=name, number=number)
        print("EnvVar: ", envVar)
        return envVar
