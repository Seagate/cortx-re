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
import re
# import sys
from datetime import datetime
from collections import defaultdict


def clean(text):
    text = text.replace('&', '&amp;')
    text = text.replace('<', '&lt;')
    text = text.replace('>', '&gt;')
    return text


class HTMLGen:
    def __init__(self, git_diff_file, html_tmpl):
        self.git_diff_files = git_diff_file
        self.html_tmpl = html_tmpl
        self.total_diff = 0

    @staticmethod
    def read_file(file):
        try:
            with open(file, 'r') as f_obj:
                data = f_obj.read()
        except FileNotFoundError as fnf_error:
            print("Error: File not found %s" % file, fnf_error)
        except IOError as io_error:
            print("Error: Cannot read file %s" % file, io_error)
        else:
            return data

    def diff_to_html(self, file):
        filename = os.path.join(self.git_diff_files, file)
        diff_data = self.read_file(filename)

        diffs_list = []
        for diff in diff_data.split('diff --git'):
            curr_file = re.sub('---\s*.*?/', './', diff)
            if '.spec' in curr_file and bool(re.search(r"^\s*[-+](?:Build)?Requires", diff)):
                diffs_list.append(diff)
            elif not '.spec' in curr_file:
                diffs_list.append(diff)

        all_diff_html = ''
        rowspan = len(diffs_list) - 1

        if len(diffs_list) > 1:
            count = 0
            for section in diffs_list:
                if section == '':
                    continue
                removed = ''
                added = ''
                curr_file = ''
                diff_html = ''
                for line in section.split('\n'):
                    if line.startswith('---'):
                        curr_file = re.sub('---\s*.*?/', './', line)
                        print("Diff found in %s" % curr_file)
                    elif '.spec' in curr_file and not bool(re.search(r"^\s*[-+](?:Build)?Requires", line)):
                        continue
                    elif line.startswith('+++'):
                        continue
                    elif line.startswith('-'):
                        removed += '<pre class="delete">%s</pre>' % line
                    elif line.startswith('+'):
                        added += '<pre class="insert">%s</pre>' % line
                if added == "":
                    added = '<pre class="insert">No package added</pre>'
                if removed == "":
                    removed = '<pre class="insert">No package removed</pre>'

                count += 1
                diff_html += '<td><h2>%s</h2><div class="file-diff">' % curr_file

                diff_html += '<pre class="context"><b>Package Added:</b></pre>%s<br>' % added
                diff_html += '<pre class="context"><b>Package Removed:</b></pre>%s' % removed
                diff_html += '</div></td>'

                if count == 1:
                    self.total_diff += 1
                    all_diff_html += '<tr><td rowspan="%s" id="reponame">%s</td>' % (rowspan, file)
                    all_diff_html += diff_html
                else:
                    all_diff_html += '<tr>%s' % diff_html

            if diff_html:
                all_diff_html += "</tr>" * rowspan
        else:
            all_diff_html += '<tr><td rowspan="1" id="reponame">%s</td>' % file
            all_diff_html += '<td><h2></h2><div class="file-diff">No Diff found</div></td></tr>'
            print('No diff found')
        return all_diff_html

    def generate_report(self):
        files = os.listdir(self.git_diff_files)
        table_data = ''
        for file in files:
            print("\nProceesing the %s..." % file)
            # filename = os.path.join(self.git_diff_files, file)
            table_data += self.diff_to_html(file)
        html_tmpl_cont = self.read_file(self.html_tmpl)
        html_tmpl_cont = html_tmpl_cont.replace('##MyCont##', table_data)
        html_tmpl_cont = html_tmpl_cont.replace('##DiffFound##', str(self.total_diff))
        html_tmpl_cont = html_tmpl_cont.replace('##MyDate##', datetime.now().strftime("%Y-%m-%d %H:%M"))
        html_tmpl_cont = html_tmpl_cont.replace('##MyTotalRepo##', str(len(files)))
        print('\n\nCreating the git_diff.html file')
        with open('git_diff.html', 'w') as w_obj:
            w_obj.write(html_tmpl_cont)


def main():
    html_obj = HTMLGen('/var/lib/jenkins/workspace/Cortx-Dev/Git-Diff/3149545061', './git_diff_template.html')
    html = html_obj.generate_report()
    print(html)

    # html_generate('/var/lib/jenkins/workspace/Cortx-Dev/Git-Diff/1631520245000')


if __name__ == '__main__':
    main()
