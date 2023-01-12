from jenkins_core import JenkinsCore
from jenkins_resources import JenkinsResources
from jenkins_nodes import JenkinsNode
from jenkins_plugins import JenkinsPlugins
from jenkins_builds import JenkinsBuilds
from mongodb import MongoDB
from datetime import datetime
import os


class Main:
    def __init__(self) -> None:
        pass

    def main(self):
        print("In Main Function")

        # Jenkins
        server_url = os.environ.get("JENKINS_SERVER_URL")
        credentials = {
            "username": os.environ.get("JENKINS_USERNAME"),
            "password": os.environ.get("JENKINS_TOKEN")
        }

        # MongoDB
        mongodb_connection_url = os.environ.get("MONGODB_CONNECTION_URL")
        mongodb_credentials = {
            "username": os.environ.get("MONGODB_USERNAME"),
            "password": os.environ.get("MONGODB_PASSWORD")
        }

        # today = date.today()
        today = datetime.now()
        today = today.strftime("%d-%b-%Y %H:%M:%S")

        mongodb = MongoDB(today=today)
        mongodb.create_connection_url(
            credentials=mongodb_credentials, connection_url=mongodb_connection_url)
        mongodb.handle_mongodb()

        if not mongodb.read_collection(mongodb.jenkins_jobs_collection):
            print("Existing documents not found...initializing")
            mongodb.insertJobsInitializationDocuments()

        if not mongodb.read_collection(mongodb.jenkins_nodes_collection):
            print("Existing documents not found...initializing")
            mongodb.insertNodesInitializationDocuments()

        if not mongodb.read_collection(mongodb.jenkins_builds_collection):
            print("Existing documents not found...initializing")
            mongodb.insertBuildsInitializationDocuments()

        # Instantiating JenkinsCore class
        jenkins_core_obj = JenkinsCore()
        jenkins_core_obj.make_connection(
            connection_url=server_url, credentials=credentials)
        jenkins_core_obj.get_jenkins_info()

        # Nodes
        jenkins_node_obj = JenkinsNode(
            today=today,
            jenkins_core_obj=jenkins_core_obj, mongodb=mongodb)
        jenkins_node_obj.get_nodes()

        # Jenkins Builds
        jenkins_build_obj = JenkinsBuilds(
            jenkins_core_obj=jenkins_core_obj, mongodb=mongodb, today=today)

        # Jenkins Resources
        jenkins_resources_obj = JenkinsResources(
            today=today,
            jenkins_core_obj=jenkins_core_obj,
            jenkins_build_obj=jenkins_build_obj,
            mongodb=mongodb)
        jenkins_resources_obj.get_total_resources_count()
        jenkins_resources_obj.get_resources()

        """
        if not mongodb.read_collection(mongodb.jenkins_plugins_collection):
            print("Existing documents not found...initializing")
            mongodb.insertPluginsInitializationDocuments()

        jenkins_plugins_obj = JenkinsPlugins(
            today=today, jenkins_core_obj=jenkins_core_obj, mongodb=mongodb)
        jenkins_plugins_obj.get_plugins()
        """


if __name__ == "__main__":
    main_obj = Main()
    main_obj.main()
