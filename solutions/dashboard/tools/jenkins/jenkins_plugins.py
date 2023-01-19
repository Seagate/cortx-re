from jenkins_core import JenkinsCore
from const import JENKINS_PLUGINS_INDEX_IDENTIFIER
from mongodb import MongoDB


class JenkinsPlugins:
    def __init__(self, today, jenkins_core_obj: JenkinsCore, mongodb: MongoDB) -> None:
        self.today = today,
        self.jenkins_core_obj = jenkins_core_obj
        self.mongodb = mongodb

    def get_plugins(self):
        plugins = self.jenkins_core_obj.server.get_plugins()
        print("Plugins: ", str(type(plugins)))

        plugins_data = []
        for k, v in plugins.items():
            print("Key: ", k)

            info = dict({
                "longName": v["longName"],
                "shortName": v["shortName"],
                "active": v["active"],
                "backupVersion":  v["backupVersion"],
                "bundled": v["bundled"],
                "deleted": v["deleted"],
                "dependencies": v["dependencies"],
                "detached": v["detached"],
                "downgradable": v["downgradable"],
                "enabled": v["enabled"],
                "hasUpdate": v["hasUpdate"],
                "pinned": v["pinned"],
                "requiredCoreVersion": v["requiredCoreVersion"],
                "supportsDynamicLoad": v["supportsDynamicLoad"],
                "url": v["url"],
                "version": "" + str(v["version"]),
                # "created_date": self.today,
                "identifier": JENKINS_PLUGINS_INDEX_IDENTIFIER,
            })

            self.mongodb.create_document(
                info, self.mongodb.jenkins_plugins_collection)
            plugins_data.append(info)
