import requests
from const import CODACY_REPOSITORY_INDEX_IDENTIFIER, CODACY_METADATA_INDEX_IDENTIFIER, PROVIDER, ORGANIZATION
from datetime import date
from mongodb import MongoDB
from repositories import Repositories


class Issues:
    def __init__(self, repositories: list, mongodb: MongoDB, main_date: date, headers: dict):
        self.repositories = repositories
        self.main_date = main_date
        self.mongodb = mongodb
        self.headers = headers

        self.levelMap = {
            "Info": "Minor",
            "Warning": "Medium",
            "Error": "Critical"
        }
        self.metadata_categories = {
            "Security": 0,
            "ErrorProne": 0,
            "Performance": 0,
            "CodeStyle": 0,
            "Compatibility": 0,
            "CodeComplexity": 0,
            "Documentation": 0,
            "UnusedCode": 0
        }
        self.metadata_levels_security = {
            "All": 0,
            "Minor": 0,
            "Medium": 0,
            "Critical": 0
        }
        self.metadata_levels_all = {
            "All": 0,
            "Minor": 0,
            "Medium": 0,
            "Critical": 0
        }

    def getRepoIssuesData(self, repository_name, params, body):
        resp = requests.post('https://app.codacy.com/api/v3/analysis/organizations/%s/%s/repositories/%s/issues/search'
                             % (PROVIDER, ORGANIZATION, repository_name),
                             params=params,
                             json=body,
                             headers=self.headers)

        # Get json data
        json_resp = resp.json()
        return json_resp

    def extractDataFromIssues(self, issues: list, categories: dict, levels_all: dict, levels_security: dict):

        # All issues Count
        levels_all["All"] += len(issues)

        # Iterate over issues
        for issue in issues:
            pattern_info = issue['patternInfo']

            # Categories
            categories[pattern_info['category']] += 1

            # Levels All
            levels_all[self.levelMap[pattern_info['level']]] += 1

            # Levels Security
            if pattern_info['category'] == "Security":
                levels_security["All"] += 1
                levels_security[self.levelMap[pattern_info['level']]] += 1

    def getIssues(self):
        repo_cnt = 1

        # Iterate over repositories
        for repository in self.repositories:
            print("\n\n(" + str(repo_cnt) + "/" +
                  str(len(self.repositories)) + ")")
            print("Repository: ", repository['repository']['name'])

            repo_cnt += 1

            # Setting up variables
            params = {}

            body = {
                "branchName": repository['repository']['defaultBranch']['name']
            }

            categories = {
                "Security": 0,
                "ErrorProne": 0,
                "Performance": 0,
                "CodeStyle": 0,
                "Compatibility": 0,
                "CodeComplexity": 0,
                "Documentation": 0,
                "UnusedCode": 0
            }

            levels_security = {
                "All": 0,
                "Minor": 0,
                "Medium": 0,
                "Critical": 0
            }

            levels_all = {
                "All": 0,
                "Minor": 0,
                "Medium": 0,
                "Critical": 0
            }

            # To get all issues
            while True:
                if "cursor" in params:
                    print("Page: ", params['cursor'])

                try:
                    # Get Issues
                    json_resp = self.getRepoIssuesData(
                        repository['repository']['name'], params, body)
                except Exception as err:
                    print("Exception: ", err)
                    raise

                # Extract data from issues response
                issues_data = json_resp['data']
                pagination = json_resp['pagination']

                # Extract data from issues
                self.extractDataFromIssues(issues=issues_data, categories=categories,
                                           levels_all=levels_all, levels_security=levels_security)

                # Pagination
                if "cursor" not in pagination:
                    break

                params['cursor'] = pagination['cursor']

            # Creating Final Repository Object
            report = {
                "coverage": repository['coverage']
            }

            if "grade" in repository:
                report["grade"] = repository["grade"]

            if "gradeLetter" in repository:
                report["gradeLetter"] = repository["gradeLetter"]

            repository_object = {
                "repository": [{
                    "id": repository['repository']['repositoryId'],
                    "name": repository['repository']['name'],
                    "visibility": repository['repository']['visibility'],
                    "lastUpdated": repository['repository']['lastUpdated']
                }],
                "branch": [{
                    "id": repository['repository']['defaultBranch']['id'],
                    "name": repository['repository']['defaultBranch']['name'],
                }],
                "report": [report],
                "categories": [categories],
                "all_issues_levels": [levels_all],
                "security_issues_levels": [levels_security],
                "created_date": self.main_date,
                "identifier": CODACY_REPOSITORY_INDEX_IDENTIFIER
            }

            print(repository_object)
            # Updating Metadata
            for key in categories:
                self.metadata_categories[key] += categories[key]

            for key in levels_all:
                self.metadata_levels_all[key] += levels_all[key]

            for key in levels_security:
                self.metadata_levels_security[key] += levels_security[key]

            # Inserting document in MongoDB
            print("")
            self.mongodb.create_document(
                data=repository_object, collection=self.mongodb.repository_collection)
            print("DONE")

        # Final Clanup - Metadata
        metadata_object = {
            "metadata_categories": [self.metadata_categories],
            "metadata_all_issues_levels": [self.metadata_levels_all],
            "metadata_security_issues_levels": [self.metadata_levels_security],
            "total_repositories": len(self.repositories),
            "created_date": self.main_date,
            "identifier": CODACY_METADATA_INDEX_IDENTIFIER,
        }

        print("\n\nMetadata: ", metadata_object)

        print("")
        self.mongodb.create_document(
            data=metadata_object, collection=self.mongodb.metadata_collection)


if __name__ == "__main__":
    repository = Repositories()
    repos = repository.getRepositories()
