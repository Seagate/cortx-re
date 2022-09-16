from datetime import date, datetime
from mongodb import MongoDB
from const import CODACY_REPOSITORY_INDEX_IDENTIFIER, CODACY_METADATA_INDEX_IDENTIFIER, PROVIDER, ORGANIZATION
import requests


class Issues:
    def __init__(self, repositories, mongodb: MongoDB, headers: dict):
        self.repositories = repositories
        self.mongodb = mongodb

        self.all_category_total_issues = 0

        self.total_security_issues = 0
        self.total_security_minor = 0
        self.total_security_major = 0
        self.total_security_critical = 0

        self.headers = headers

    def getRepositoryIssuesCategories(self, repository):
        print("Getting all category issues")

        resp = None
        repo_category_data = {}
        categories = []
        issues_count = 0

        try:
            resp = requests.get(
                'https://app.codacy.com/api/v3/analysis/organizations/%s/%s/repositories/%s/category-overviews' % (
                    PROVIDER, ORGANIZATION, repository),
                params={},
                headers=self.headers)

            resp = resp.json()

            for item in resp['data']:
                if item['totalResults'] > 0:
                    issues_count += item['totalResults']
                    category_data = {}
                    category_data[item['category']
                                  ['categoryType']] = item['totalResults']
                    categories.append(category_data)

            self.all_category_total_issues += issues_count

            repo_category_data = {
                "repository_total_issues": issues_count,
                "categories": categories
            }
        except Exception as err:
            print("Search Issues Categories Exception: ", err)

        return repo_category_data

    def getSecurityIssues(self, repository):
        print("Getting security issues")

        minor = 0
        major = 0
        critical = 0

        params = {}  # cursor will dynamically added
        repository_security_data = {}
        try:
            while True:
                resp = requests.post(
                    'https://app.codacy.com/api/v3/analysis/organizations/%s/%s/repositories/%s/issues/search' % (
                        PROVIDER, ORGANIZATION, repository),
                    params=params,
                    json={
                        "categories": [
                            "Security"
                        ]
                    },
                    headers=self.headers)

                resp = resp.json()

                # Looping through issues to find counts
                for issue in resp['data']:
                    severityLevel = issue['patternInfo']['severityLevel']

                    if severityLevel == 'Info':  # Minor
                        minor += 1
                    elif severityLevel == 'Warning':  # Major
                        major += 1
                    elif severityLevel == 'Error':  # Critical
                        critical += 1

                # Check if next is there or not
                if "cursor" in resp['pagination']:
                    params['cursor'] = resp['pagination']['cursor']
                    print("Going to page-", resp['pagination']['cursor'])
                else:
                    break

            # Collecting all data
            repository_security_data = {
                "issues": minor+major+critical,
                "minor": minor,
                "medium": major,
                "critical": critical
            }

            self.total_security_issues += minor+major+critical
            self.total_security_minor += minor
            self.total_security_major += major
            self.total_security_critical += critical
        except Exception as err:
            print("Search Issues Exception: ", err)

        return repository_security_data

    def handle_issues(self):
        repo_curr_index = 1

        today = date.today()
        today = today.strftime("%d-%b-%Y")

        for repository in self.repositories:
            print("\n(" + str(repo_curr_index) + "/" +
                  str(len(self.repositories)) + ")")
            print("Repository: {}".format(repository))
            category_issues = self.getRepositoryIssuesCategories(
                repository=repository)
            security_issues = self.getSecurityIssues(repository=repository)

            repositories_document = {
                "repository": repository,
                "categories": category_issues['categories'],
                "all_categories": [{
                    "total_issues": category_issues['repository_total_issues']
                }],
                "security": [security_issues],
                "codacy_repository_link": "https://app.codacy.com/gh/Seagate/" + repository + "/issues",
                "identifier": CODACY_REPOSITORY_INDEX_IDENTIFIER,
                "created_date": today,
                "created_at": datetime.now(),
            }

            print("Repository Document: {}".format(repositories_document))
            self.mongodb.create_document(
                data=repositories_document, collection=self.mongodb.repository_collection)
            print("")
            repo_curr_index += 1

        metadata_document = {
            "all_categories": [{
                "total_issues": self.all_category_total_issues
            }],
            "security": [{
                "total_issues": self.total_security_issues,
                "minor_issues": self.total_security_minor,
                "major_issues": self.total_security_major,
                "critical_issues": self.total_security_critical,
            }],
            "codacy_link": "https://app.codacy.com/gh/Seagate/",
            "identifier": CODACY_METADATA_INDEX_IDENTIFIER,
            "created_date": today,
            "created_at": datetime.now(),
        }

        print("Metadata Document: {}".format(metadata_document))
        self.mongodb.create_document(
            data=metadata_document, collection=self.mongodb.metadata_collection)
        print("")
