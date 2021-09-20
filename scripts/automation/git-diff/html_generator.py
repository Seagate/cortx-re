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
import os
import sys
import re
from datetime import datetime
from collections import defaultdict


def clean(text):
    text = text.replace('&', '&amp;')
    text = text.replace('<', '&lt;')
    text = text.replace('>', '&gt;')
    return text

class HTMLGen:
    def __init__(self, git_diff_file, html_tmpl):
        self.git_diff_file = git_diff_file
        self.html_tmpl = html_tmpl

    @staticmethod
    def read_file(file):
        try:
            with open(file, 'r') as f_obj:
                data = f_obj.read()
        except IOError as io_error:
            print(io_error)
        except FileNotFoundError as fnf_error:
            print(fnf_error)
        else:
            return data

    @staticmethod
    def add_diff_to_page(header, curr_section, row_span):
        html = "<tr>%s<td><h2>%s</h2><div class='file-diff'>%s</div></td></tr>" %(row_span, header, curr_section)
        return html

    def html_git_diff(self):
        curr_file = ''
        curr_section = ''
        diff_seen = 0
        html_cls = ''
        html_data = ''
        td_rowspan = ''
        diff_count = 0
        row_span = defaultdict(int)
        html_tmpl_cont = self.read_file(self.html_tmpl)
        with open(self.git_diff_file, 'r') as f_obj:
            for line in f_obj:
                sub_string_8 = line[0:8]
                sub_string_4 = line[0:4]
                if sub_string_8 == 'RepoName':
                   repo_name = line.replace('RepoName: ', '')
                if sub_string_4 == 'diff':
                   row_span[repo_name] += 1

        with open(self.git_diff_file, 'r') as f_obj:
            for line in f_obj:
                sub_string_1 = line[0:1]
                sub_string_2 = line[0:2]
                sub_string_3 = line[0:3]
                sub_string_4 = line[0:4]
                sub_string_7 = line[0:7]
                sub_string_8 = line[0:8]
                if sub_string_8 == 'RepoName':
                    repo_name = line.replace('RepoName: ', '')
                    td_rowspan = "<td rowspan='%s' id='reponame'>%s</td>" %(row_span[repo_name],repo_name)
                    continue
                elif sub_string_7 == 'diff no':
                    html_data += self.add_diff_to_page(curr_file, 'No Diff found', td_rowspan)
                    curr_section = ''
                    continue
                elif sub_string_4 == "diff":
                    html_cls = 'file'
                    if diff_seen == 1:
                        html_data += self.add_diff_to_page(curr_file, curr_section, td_rowspan)
                        td_rowspan = ''
                    diff_count += 1
                    diff_seen = 1
                elif sub_string_3 == '---':
                    html_cls = 'delete'
                    curr_file = re.sub('---\s*.*/', '', line)
                elif sub_string_3 == '+++':
                    html_cls = 'insert'
                elif sub_string_2 == '@@':
                    html_cls = 'info'
                elif sub_string_1 == '+':
                    html_cls = 'insert'
                elif sub_string_1 == '-':
                    html_cls = 'delete'
                else:
                    html_cls = 'context'
                line = clean(line)
                if html_cls:
                    curr_section += '<pre class="%s">%s</pre>' %(html_cls, line)
                else:
                    curr_section += '<pre>%s</pre>' %line
            if curr_section:
                curr_section += "</div>"
                html_data += self.add_diff_to_page(curr_file, curr_section, td_rowspan)
            html_tmpl_cont = html_tmpl_cont.replace('##MyCont##', html_data)
            html_tmpl_cont = html_tmpl_cont.replace('##DiffFound##', str(diff_count))
            html_tmpl_cont = html_tmpl_cont.replace('##MyDate##', datetime.now().strftime("%Y-%m-%d %H:%M"))
            html_tmpl_cont = html_tmpl_cont.replace('##MyTotalRepo##', str(len(row_span.keys())))
        with open('git_diff.html', 'w') as w_obj:
            w_obj.write(html_tmpl_cont)

def main():
    html_obj = HTMLGen('/var/lib/jenkins/workspace/Cortx-Dev/Git-Diff/3552625012', './git_diff_template.html')
    html = html_obj.html_git_diff()
    # print (html)

    # html_generate('/var/lib/jenkins/workspace/Cortx-Dev/Git-Diff/1631520245000')

if __name__ == '__main__':
    main()
