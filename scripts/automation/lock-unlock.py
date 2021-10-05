#
# Copyright (c) 2021 Seagate Technology LLC and/or its Affiliates
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU Affero General Public License as published
# by the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
# GNU Affero General Public License for more details.
# You should have received a copy of the GNU Affero General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.
# For any questions about this software or licensing,
# please email opensource@seagate.com or cortx-questions@seagate.com.
#

import requests
import argparse

def get_args():
    # Create the parser
    parser = argparse.ArgumentParser()
    # Add the arguments
    parser.add_argument('--repo', '-r', required=True)
    parser.add_argument('--branch', '-b', required=True)
    parser.add_argument('--flag', '-f', required=True)
    parser.add_argument('--token', '-t', required=True)
    # Execute the parse_args() method and return
    return parser.parse_args()


def get_name(lists):
    data = []
    for list in lists:
        data.append(list['name'])
    return data


def main():
    args = get_args()
    repo = args.repo
    branch = args.branch
    flag = args.flag
    access_token = args.token

    repos = repo.split(',')
    branches = branch.split(',')
    payload = {}

    for branch in branches:
        for repo in repos:
            print ("Processing %s......." % (repo))
            url = 'https://api.github.com/repos/seagate/{0}/branches/{1}/protection'.format(repo, branch)
            headers = {
                'Accept': 'application/vnd.github.luke-cage-preview+json',
                'Authorization': 'Token {0}'.format(access_token)
            }
            get_response = requests.get(url, headers=headers)

            if get_response.status_code == 200:
                res = get_response.json()
                teams = []
                apps = []
                users = []
                enforce_admins = ''
                required_status_checks = None
                required_status_checks_flag = False
                required_linear_history = True
                require_code_owner_reviews = True
                required_approving_review_count = 2

                if 'restrictions' in res:
                    teams = get_name(res['restrictions']['teams'])
                    apps = get_name(res['restrictions']['apps'])
                    users = get_name(res['restrictions']['users'])
                if 'enforce_admins' in res:
                    enforce_admins = res['enforce_admins']['enabled']
                if 'required_status_checks' in res:
                    required_status_checks = {
                        "contexts": res['required_status_checks']['contexts'],
                        "strict": res['required_status_checks']['strict']
                    }
                if 'required_linear_history' in res:
                    required_linear_history = res['required_linear_history']['enabled']
                if 'required_pull_request_reviews' in res:
                    require_code_owner_reviews = res['required_pull_request_reviews']['require_code_owner_reviews']
                    required_approving_review_count = res['required_pull_request_reviews'][
                        'required_approving_review_count']

                payload = {
                    "required_status_checks": required_status_checks,
                    "enforce_admins": enforce_admins,
                    "required_pull_request_reviews": {
                        "require_code_owner_reviews": require_code_owner_reviews,
                        "required_approving_review_count": required_approving_review_count
                    },
                    "restrictions": {
                        "users": users,
                        "teams": teams,
                        "apps": apps
                    },
                    "required_linear_history": required_linear_history,
                }

                if flag == "Lock":
                    payload['enforce_admins'] = True
                    payload['restrictions'] = None
                else:
                    payload['enforce_admins'] = False
                    teams.append("%s-gatekeepers" % (repo))
                    # teams.append("%s-admins" %("cortx-devops"))
                    payload['restrictions']['teams'] = teams

            put_respose = requests.put(url, headers=headers, json=payload)

            if put_respose.status_code == 200:
                print ('%sed the branch %s in %s repo' % (flag, branch, repo))
                print ("\n")
            else:
                print (put_respose.json())
                print ("Info: Error while processing the request")
                exit(1)


if __name__ == "__main__":
    main()