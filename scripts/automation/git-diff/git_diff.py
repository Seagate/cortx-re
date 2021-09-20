#
# Copyright (c) 2021 Seagate Technology LLC and/or its Affiliates
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# For any questions about this software or licensing,
# please email opensource@seagate.com or cortx-questions@seagate.com.
#
from git import Repo, exc
from datetime import datetime, timedelta
from builtins import Exception
import argparse
import random
import os
import json
from html_generator import HTMLGen

# files = ['LICENSE', 'README.md']

def get_args():
    """
    Read the parameter from commandline
    Returns:
    parser (list): Parsed commandline parameter
    """
    # Create the parser
    parser = argparse.ArgumentParser()
    # Add the arguments

    parser.add_argument('--config', '-c', required=True, help="Config Json")
    parser.add_argument('--src_branch', '-s', help="Source Branch")
    parser.add_argument('--dest_branch', '-x', help="Destnation Branch")
    parser.add_argument('--diff_range', '-d', required=True, choices=['Today', 'Last-Day', 'Last-Week', 'Last-Month', 'Last-Year'],
                        default="Last-Day", help="Date Range to get diff in package files")

    # Execute the parse_args() method and return
    return parser.parse_args()

class GitDiff:
    def __init__(self, params):
        self.now = datetime.now()
        self.src_branch = params.src_branch
        self.dest_branch = params.dest_branch
        self.config = eval(HTMLGen.read_file(params.config))

        days_range = ''
        if params.diff_range == "Today":
            self.date_from = self.now.strftime("%Y-%m-%d 00:00")
            self.date_to = self.now.strftime("%Y-%m-%d %H:%M")
        elif params.diff_range == "Last-Day":
            days_range = 1
        elif params.diff_range == "Last-Week":
            days_range = 7
        elif params.diff_range == "Last-Month":
            days_range = 30
        elif params.diff_range == "Last-Year":
            days_range = 365

        if days_range:
            self.date_from = (self.now - timedelta(days=days_range)).strftime("%Y-%m-%d 00:00")
            self.date_to = (self.now - timedelta(days=1)).strftime("%Y-%m-%d 23:59")

    def process(self):
        try:
            filename = str(random.randrange(10000, 10**10))
            with open(filename, 'a') as f_obj:
                for repo_dict in self.config['repository']:
                    repo = repo_dict['name']

                    repo_obj = Repo(repo)
                    if self.src_branch and self.dest_branch:
                        diff_string = repo_obj.git.diff(self.src_branch, self.dest_branch)
                    else:
                        logs = repo_obj.git.log("--after='%s'" % self.date_from, "--before='%s'" % self.date_to,
                                                "--pretty=%H")
                        latest_commit = logs.splitlines()
                        if len(latest_commit) > 0:
                            logs_before = repo_obj.git.log("--before='%s'" % self.date_from, "--pretty=%H")
                            last_day_commit = logs_before.splitlines()
                            if len(last_day_commit) > 0:
                                #print (latest_commit[0], last_day_commit[0])
                                diff_string = repo_obj.git.diff(latest_commit[0], last_day_commit[0], repo_dict['files'])
                                f_obj.write(diff_string)
                            else:
                                print("No last day commit found for %s.." % repo)
                                f_obj.write('RepoName: %s\ndiff not found\n' % repo)
                        else:
                           print("No latest commits found for %s.." %repo)
                           f_obj.write('RepoName: %s\ndiff not found\n' % repo)
        except IOError as io_error:
            print('Error while writing files', {io_error})
        except exc.GitCommandError as git_error:
            print('Error creating remote:', {git_error})
        else:
            print("Generating HTML")
            html_obj = HTMLGen(filename, './git_diff_template.html')
            html = html_obj.html_git_diff()
            #print (filename)

def main():
    args = get_args()
    diff_obj = GitDiff(args)
    diff_obj.process()

if __name__ == '__main__':
    main()
