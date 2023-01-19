import jenkins


class JenkinsCore:
    def __init__(self) -> None:
        self.server = None

    def make_connection(self, connection_url: str, credentials: dict):
        # Trying 5 times to establish the connection with the jenkins server
        for i in range(1, 5):
            try:
                self.server = jenkins.Jenkins(
                    url=connection_url,
                    username=credentials["username"],
                    password=credentials["password"],
                )
                self.server.get_whoami()
                print("(" + str(i) + "/5) Connection Established")
                break
            except Exception as err:
                print("(" + str(i) + "/5) Exception: Connection Not Established ", err)

        self.server.get_whoami()

    def get_jenkins_info(self):
        try:
            self.user = self.server.get_whoami()
            self.version = self.server.get_version()

            print("\nUser: ", self.user["fullName"])
            print("Jenkins Version: ", self.version, end="\n\n")

        except Exception as err:
            print("Exception: Get Jenkins Info Not Working, ", err)
