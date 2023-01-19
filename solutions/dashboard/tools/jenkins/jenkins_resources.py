import xmltodict
from jenkins_core import JenkinsCore
from jenkins_builds import JenkinsBuilds
from mongodb import MongoDB
from const import JENKINS_JOBS_INDEX_IDENTIFIER


class JenkinsResources:
    def __init__(self, today, jenkins_core_obj: JenkinsCore, jenkins_build_obj: JenkinsBuilds, mongodb: MongoDB) -> None:
        self.today = today
        self.jenkins_core_obj = jenkins_core_obj
        self.jenkins_build_obj = jenkins_build_obj
        self.mongodb = mongodb
        self.resources = {}
        self.total_resources = 0

    def get_total_resources_count(self):
        self.total_resources = self.jenkins_core_obj.server.jobs_count()
        print("Total Resources: ", self.total_resources, end="\n\n")

    def get_resources(self):
        try:
            all_resources = self.jenkins_core_obj.server.get_all_jobs()

            # iterate through the data of complete hierarchy
            i = 1
            for item in all_resources:
                print("\n(" + str(i) + "/" + str(self.total_resources) + ")")
                print("JobName: ", item["fullname"], "->", item["_class"])
                filtered_data = {
                    "_class": item["_class"],
                    "jobname": item["name"],
                    "jobfullname": item["fullname"],
                    "joburl": item["url"],
                }

                try:
                    resource_info = self.jenkins_core_obj.server.get_job_info(
                        name=item["fullname"])

                    # Extracting resource information
                    if resource_info["_class"] == "hudson.model.FreeStyleProject":
                        job_data = self.get_job_data(job_data=resource_info)
                        filtered_data.update(job_data)

                        job_config_data = self.get_job_config(
                            job_name=filtered_data["jobfullname"])

                        filtered_data.update(job_config_data)

                        filtered_data["identifier"] = JENKINS_JOBS_INDEX_IDENTIFIER
                        filtered_data["created_date"] = self.today
                        filtered_data["category"] = "FreeStyleProject"

                        extra_data = self.jenkins_build_obj.get_builds(
                            fullname=filtered_data["jobfullname"], name=filtered_data["jobname"], builds=resource_info["builds"], category="FreeStyleProject")

                        filtered_data["isOlder"] = extra_data["isOlder"]
                        filtered_data["labelname"] = extra_data["labelname"]
                        filtered_data["nodename"] = extra_data["nodename"]

                        self.mongodb.create_document(
                            filtered_data, self.mongodb.jenkins_jobs_collection)

                    elif resource_info["_class"] == "org.jenkinsci.plugins.workflow.job.WorkflowJob":
                        pipeline_data = self.get_pipeline_data(
                            pipeline_data=resource_info)
                        filtered_data.update(pipeline_data)

                        pipeline_config_data = self.get_pipeline_config(
                            pipeline_name=filtered_data["jobfullname"])
                        filtered_data.update(pipeline_config_data)

                        filtered_data["identifier"] = JENKINS_JOBS_INDEX_IDENTIFIER
                        filtered_data["created_date"] = self.today
                        filtered_data["category"] = "WorkflowJob"

                        extra_data = self.jenkins_build_obj.get_builds(
                            fullname=filtered_data["jobfullname"], name=filtered_data["jobname"], builds=resource_info["builds"], category="WorkflowJob")

                        filtered_data["isOlder"] = extra_data["isOlder"]
                        filtered_data["nodename"] = extra_data["nodename"]

                        self.mongodb.create_document(
                            filtered_data, self.mongodb.jenkins_jobs_collection)

                    elif resource_info["_class"] == "com.cloudbees.hudson.plugins.folder.Folder":
                        folder_data = self.get_folder_data(
                            folder_data=resource_info)
                        filtered_data.update(folder_data)

                        filtered_data["identifier"] = JENKINS_JOBS_INDEX_IDENTIFIER
                        filtered_data["created_date"] = self.today
                        filtered_data["category"] = "Folder"
                        self.mongodb.create_document(
                            filtered_data, self.mongodb.jenkins_jobs_collection)

                    elif resource_info["_class"] == "hudson.matrix.MatrixProject":
                        multiconfiguration_project_data = self.get_multiconfiguration_project_data(
                            multiconfiguration_project_data=resource_info)

                        multiconfiguration_config_data = self.get_multiconfiguration_project_config(
                            project_name=filtered_data["jobfullname"])
                        filtered_data.update(multiconfiguration_config_data)

                        filtered_data.update(multiconfiguration_project_data)

                        filtered_data["identifier"] = JENKINS_JOBS_INDEX_IDENTIFIER
                        filtered_data["created_date"] = self.today
                        filtered_data["category"] = "MatrixProject"

                        extra_data = self.jenkins_build_obj.get_builds(
                            fullname=filtered_data["jobfullname"], name=filtered_data["jobname"], builds=resource_info["builds"], category="MatrixProject")

                        filtered_data["isOlder"] = extra_data["isOlder"]
                        filtered_data["labelname"] = extra_data["labelname"]

                        self.mongodb.create_document(
                            filtered_data, self.mongodb.jenkins_jobs_collection)

                    # Append data to resources
                    if item["_class"] in self.resources:
                        self.resources[item["_class"]].append(filtered_data)
                    else:
                        self.resources[item["_class"]] = [filtered_data]

                    i += 1
                except Exception as err:
                    print("Exception: Get Job Information ", err)

        except Exception as err:
            print("Exception: Get Resources ", err)

    def get_build_details(self, data: dict):
        filtered_data = {}

        if data["firstBuild"] is not None:
            filtered_data["firstBuild"] = data["firstBuild"]["number"]
        if data["lastBuild"] is not None:
            filtered_data["lastBuild"] = data["lastBuild"]["number"]
        if data["lastCompletedBuild"] is not None:
            filtered_data["lastCompletedBuild"] = data["lastCompletedBuild"]["number"]
        if data["lastFailedBuild"] is not None:
            filtered_data["lastFailedBuild"] = data["lastFailedBuild"]["number"]
        if data["lastStableBuild"] is not None:
            filtered_data["lastStableBuild"] = data["lastStableBuild"]["number"]
        if data["lastUnstableBuild"] is not None:
            filtered_data["lastUnstableBuild"] = data["lastUnstableBuild"]["number"]
        if data["lastSuccessfulBuild"] is not None:
            filtered_data["lastSuccessfulBuild"] = data["lastSuccessfulBuild"]["number"]
        if data["lastUnsuccessfulBuild"] is not None:
            filtered_data["lastUnsuccessfulBuild"] = data["lastUnsuccessfulBuild"]["number"]
        if data["nextBuildNumber"] is not None:
            filtered_data["nextBuildNumber"] = data["nextBuildNumber"]

        return filtered_data

    def get_actions(self, data: dict):
        filtered_data = {}
        actions_list = []

        for action in data["actions"]:
            if "_class" in action:
                actions_list.append(action["_class"])

        filtered_data["jobactions"] = actions_list
        return filtered_data

    def get_properties(self, data: dict):
        filtered_data = {}

        properties_list = []
        for data_property in data["property"]:
            if "_class" in data_property:
                properties_list.append(data_property["_class"])

                # Checking for parameters
                if data_property["_class"] == "hudson.model.ParametersDefinitionProperty":
                    if len(data_property["parameterDefinitions"]) > 0:
                        params_list = []
                        for param in data_property["parameterDefinitions"]:
                            params_list.append(param["name"])
                        filtered_data["jobparameters"] = params_list

        filtered_data["jobproperty"] = properties_list
        return filtered_data

    def get_active_configurations(self, data: dict):
        filtered_data = {}
        active_configurations_list = []

        for action in data["activeConfigurations"]:
            if "_class" in action:
                active_configurations_list.append(action["name"])

        filtered_data["activeConfigurations"] = active_configurations_list
        return filtered_data

    def get_downstream_projects(self, data: dict):
        filtered_data = {}
        downstream_projects_list = []

        for project in data["downstreamProjects"]:
            downstream_projects_list.append(project["name"])

        filtered_data["downstreamProjects"] = downstream_projects_list
        return filtered_data

    def get_upstream_projects(self, data: dict):
        filtered_data = {}
        upstream_projects_list = []

        for project in data["upstreamProjects"]:
            upstream_projects_list.append(project["name"])

        filtered_data["upstreamProjects"] = upstream_projects_list
        return filtered_data

    def get_job_data(self, job_data: dict):
        filtered_data = {
            "jobdescription": job_data["description"],
            "buildable": job_data["buildable"],
            "color": job_data["color"],
            "inQueue": job_data["inQueue"],
            "queueItem": job_data["queueItem"],
            "concurrentBuild": job_data["concurrentBuild"],
            "keepDependencies": job_data["keepDependencies"],
            "disabled": job_data["disabled"],
        }

        # Label Expression
        if job_data["labelExpression"] is not None:
            filtered_data["labelExpression"] = job_data["labelExpression"]

        # Health Score
        if len(job_data["healthReport"]) > 0:
            data = job_data["healthReport"][0]
            filtered_data["healthScore"] = data["score"]
            filtered_data["healthDescription"] = data["description"]

        # Builds Information
        build_details = self.get_build_details(job_data)
        filtered_data.update(build_details)

        # Actions
        if len(job_data["actions"]) > 0:
            actions_data = self.get_actions(job_data)
            filtered_data.update(actions_data)

        # Properties
        if len(job_data["property"]) > 0:
            properties_data = self.get_properties(job_data)
            filtered_data.update(properties_data)

        # downstreamProjects
        if len(job_data["downstreamProjects"]) > 0:
            downstream_projects_data = self.get_downstream_projects(
                data=job_data)
            filtered_data.update(downstream_projects_data)

        # downstreamProjects
        if len(job_data["downstreamProjects"]) > 0:
            downstream_projects_data = self.get_downstream_projects(
                data=job_data)
            filtered_data.update(downstream_projects_data)

        # upstreamProjects
        if len(job_data["upstreamProjects"]) > 0:
            upstream_projects_data = self.get_upstream_projects(data=job_data)
            filtered_data.update(upstream_projects_data)

        return filtered_data

    def get_pipeline_data(self, pipeline_data: dict):
        filtered_data = {
            "jobdescription": pipeline_data["description"],
            "buildable": pipeline_data["buildable"],
            "color": pipeline_data["color"],
            "inQueue": pipeline_data["inQueue"],
            "queueItem": pipeline_data["queueItem"],
            "concurrentBuild": pipeline_data["concurrentBuild"],
            "keepDependencies": pipeline_data["keepDependencies"],
        }

        # Builds Information
        build_details = self.get_build_details(pipeline_data)
        filtered_data.update(build_details)

        # Actions
        if len(pipeline_data["actions"]) > 0:
            actions_data = self.get_actions(pipeline_data)
            filtered_data.update(actions_data)

        # Properties
        if len(pipeline_data["property"]) > 0:
            properties_data = self.get_properties(pipeline_data)
            filtered_data.update(properties_data)

        return filtered_data

    def get_folder_data(self, folder_data: dict):
        filtered_data = {
            "jobdescription": folder_data["description"]
        }

        # Health Score
        if len(folder_data["healthReport"]) > 0:
            data = folder_data["healthReport"][0]
            filtered_data["healthScore"] = data["score"]
            filtered_data["healthDescription"] = data["description"]

        # Primary View
        if folder_data["primaryView"] is not None:
            filtered_data["primaryView"] = folder_data["primaryView"]["name"]

        # Views
        if len(folder_data["views"]) > 0:
            views_list = []
            for view in folder_data["views"]:
                views_list.append(view["name"])
            filtered_data["views"] = views_list

        # Actions
        if len(folder_data["actions"]) > 0:
            actions_data = self.get_actions(folder_data)
            filtered_data.update(actions_data)

        return filtered_data

    def get_multiconfiguration_project_data(self, multiconfiguration_project_data: dict):
        filtered_data = {
            "jobdescription": multiconfiguration_project_data["description"],
            "buildable": multiconfiguration_project_data["buildable"],
            "color": multiconfiguration_project_data["color"],
            "inQueue": multiconfiguration_project_data["inQueue"],
            "queueItem": multiconfiguration_project_data["queueItem"],
            "concurrentBuild": multiconfiguration_project_data["concurrentBuild"],
            "keepDependencies": multiconfiguration_project_data["keepDependencies"],
        }

        # Label Expression
        if multiconfiguration_project_data["labelExpression"] is not None:
            filtered_data["labelExpression"] = multiconfiguration_project_data["labelExpression"]

        # Builds Information
        build_details = self.get_build_details(multiconfiguration_project_data)
        filtered_data.update(build_details)

        # Actions
        if len(multiconfiguration_project_data["actions"]) > 0:
            actions_data = self.get_actions(multiconfiguration_project_data)
            filtered_data.update(actions_data)

        # Properties
        if len(multiconfiguration_project_data["property"]) > 0:
            properties_data = self.get_properties(
                multiconfiguration_project_data)
            filtered_data.update(properties_data)

        # downstreamProjects
        if len(multiconfiguration_project_data["downstreamProjects"]) > 0:
            downstream_projects_data = self.get_downstream_projects(
                data=multiconfiguration_project_data)
            filtered_data.update(downstream_projects_data)

        # upstreamProjects
        if len(multiconfiguration_project_data["upstreamProjects"]) > 0:
            upstream_projects_data = self.get_upstream_projects(
                data=multiconfiguration_project_data)
            filtered_data.update(upstream_projects_data)

        if len(multiconfiguration_project_data["activeConfigurations"]) > 0:
            active_configurations_data = self.get_active_configurations(
                data=multiconfiguration_project_data)
            filtered_data.update(active_configurations_data)

        return filtered_data

    def get_job_config(self, job_name: str):
        job_config = self.jenkins_core_obj.server.get_job_config(name=job_name)

        data = {}
        # Converting XML data into JSON
        job_config_json = xmltodict.parse(job_config)

        # Taking SCM data
        if "scm" in job_config_json["project"] and "userRemoteConfigs" in job_config_json["project"]["scm"]:
            scm_data = job_config_json["project"]["scm"]
            local_scm_data = {}
            if "userRemoteConfigs" in scm_data:
                if "hudson.plugins.git.UserRemoteConfig" in scm_data["userRemoteConfigs"]:
                    if "url" in scm_data["userRemoteConfigs"]["hudson.plugins.git.UserRemoteConfig"]:
                        local_scm_data["url"] = scm_data["userRemoteConfigs"]["hudson.plugins.git.UserRemoteConfig"]["url"]
            if "branches" in scm_data:
                if "hudson.plugins.git.BranchSpec" in scm_data["branches"]:
                    local_scm_data["branch"] = scm_data["branches"]["hudson.plugins.git.BranchSpec"]["name"]

            if local_scm_data != {}:
                data["scm"] = [local_scm_data]

        if "triggers" not in job_config_json["project"] or job_config_json["project"]["triggers"] is None:
            return data

        job_triggers = job_config_json["project"]["triggers"]

        # Taking the triggers data from config JSON
        triggers = []

        for key in job_triggers:
            triggers.append(key)

            if key == "hudson.triggers.TimerTrigger":
                data["TimerTrigger"] = [{
                    "scheduled": True,
                    "schedule": job_triggers[key]["spec"],
                }]

            if key == "hudson.triggers.SCMTrigger":
                data["SCMTrigger"] = [dict(job_triggers[key])]

        data["triggers"] = triggers

        return data

    def get_pipeline_config(self, pipeline_name: str):
        job_config = self.jenkins_core_obj.server.get_job_config(
            name=pipeline_name)

        data = {}
        # Converting XML data into JSON
        job_config_json = xmltodict.parse(job_config)

        # Taking SCM data
        if "definition" in job_config_json["flow-definition"]:
            if "scm" in job_config_json["flow-definition"]["definition"]:
                if "userRemoteConfigs" in job_config_json["flow-definition"]["definition"]["scm"]:
                    scm_data = job_config_json["flow-definition"]["definition"]["scm"]
                    local_scm_data = {}

                    if "userRemoteConfigs" in scm_data:
                        if "hudson.plugins.git.UserRemoteConfig" in scm_data["userRemoteConfigs"]:
                            user_remote_config = scm_data["userRemoteConfigs"]["hudson.plugins.git.UserRemoteConfig"]

                            if "url" in user_remote_config:
                                local_scm_data["url"] = user_remote_config["url"]

                    if "branches" in scm_data:
                        if "hudson.plugins.git.BranchSpec" in scm_data["branches"]:
                            local_scm_data["branch"] = scm_data["branches"]["hudson.plugins.git.BranchSpec"]["name"]

                    if local_scm_data != {}:
                        data["scm"] = [local_scm_data]

        if "properties" not in job_config_json["flow-definition"]:
            return data

        if job_config_json["flow-definition"]["properties"] is None:
            return data

        properties = job_config_json["flow-definition"]["properties"]

        for data_property in properties:
            if data_property == "com.coravy.hudson.plugins.github.GithubProjectProperty":
                if "projectUrl" in properties[data_property] and properties[data_property]["projectUrl"] is not None:
                    data["projectUrl"] = properties[data_property]["projectUrl"]

            if data_property == "org.jenkinsci.plugins.workflow.job.properties.PipelineTriggersJobProperty":
                if "triggers" in properties[data_property] and properties[data_property]["triggers"] is not None:
                    triggers = []

                    for key in properties[data_property]["triggers"]:
                        triggers.append(key)

                        if key == "hudson.triggers.TimerTrigger":
                            data["TimerTrigger"] = [{
                                "scheduled": True,
                                "schedule": properties[data_property]["triggers"][key]["spec"]
                            }]

                        if key == "hudson.triggers.SCMTrigger":
                            data["SCMTrigger"] = [
                                dict(properties[data_property]["triggers"][key])]

                    data["triggers"] = triggers

        return data

    def get_multiconfiguration_project_config(self, project_name: str):
        job_config = self.jenkins_core_obj.server.get_job_config(
            name=project_name)

        # Converting XML data into JSON
        job_config_json = xmltodict.parse(job_config)

        if "triggers" not in job_config_json["matrix-project"] or job_config_json["matrix-project"]["triggers"] is None:
            return {}

        job_triggers = job_config_json["matrix-project"]["triggers"]

        # Taking the triggers data from config JSON
        data = {}
        triggers = []

        for key in job_triggers:
            triggers.append(key)

            if key == "hudson.triggers.TimerTrigger":
                data["TimerTrigger"] = [{
                    "scheduled": True,
                    "schedule": job_triggers[key]["spec"],
                }]

            if key == "hudson.triggers.SCMTrigger":
                data["SCMTrigger"] = [dict(job_triggers[key])]

        data["triggers"] = triggers

        return data
