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
import argparse
from datetime import datetime
from collections import defaultdict

eof = ''
eof_file = ''
TOTAL_WORDS_SCANNED = 0
TOTAL_FILES_SCANNED = 0
CUSTOM_CONT = []

def get_args():
    """
    Read the parameter from commandline
    Returns:
    parser (list): Parsed commandline parameter
    """
    # Create the parser
    parser = argparse.ArgumentParser()

    # Add the arguments
    parser.add_argument('--file', '-f', required=True, help="Alex scanned file")

    # Execute the parse_args() method and return
    return parser.parse_args()

def get_block_content(file_name):
    """
    Get the block of file content from the scan
    Returns:
    generator object (): Object with block of content
    """
    count = 0
    global eof
    global eof_file
    file_dict = {}
    print ("Generator function called..")
    with open(file_name, "r") as fp:
        component = file_name.replace('.alex', '')
        component = component.strip()
        filename = ''
        for line in fp:
            # print("f.seek", os.SEEK_END, os.SEEK_SET)
            if 'no issues found' in line:
                continue
            elif line and line[0].isalpha():
                filename = line
                print ("Processing the %s" %filename)
                file_dict[filename] = ''
                if count > 0:
                    count = 0
                    yield file_dict
                    file_dict = {}
                    file_dict[filename] = ''
                    eof = ''
                    eof_file = ''
            else:
                file_dict[filename] += line
                eof += line
                count += 1
                eof_file = filename


def html_row_gen(file, word_cont, component):
    """
    Generate the tables rows.
    Returns:
    html_content: html row content
    """
    if file == "":
       return ""
    print ("Generate row %s" % file)
    tabe_row = '<tr><td><h2>%s</h2><div class="file-diff"><table class="inner">' % file
    header = '<tr><th class="word_width">Word</th><th class="line_width">Line Number</th><th>Recommendation</th></tr>'
    words_list = word_cont.split('\n')
    rows = ""
    global TOTAL_WORDS_SCANNED
    for word_line in words_list:
        word_line = re.sub(r"\s+", " ", word_line)
        word_line = word_line.strip()
        word = word_line.split(' ')
        recommand = ' '.join(word[2:-2])
        word_obj = re.search(r'`[a-z]+`', recommand)
        if len(word) > 2 and word_obj :
            TOTAL_WORDS_SCANNED += 1
            rows += '<tr><td class="word_width">%s</td>' % word_obj.group()
            rows += '<td class="line_width">%s</td>' % word[0].split('-')[0]
            rows += '<td><pre>%s<pre></td></tr>' % recommand

    component_name = component.replace('.alex', '.custom')
    custom_scan_cont = ''
    with open(component_name, "r") as fc:
       custom_scan_cont = fc.read()
       custom_scan_cont = re.sub('Binary file[^>]*?\\n', '', custom_scan_cont)
       file = file.strip()
       if file in custom_scan_cont:
          #global CUSTOM_CONT
          pattern = '%s[^>]+?\\n' %file
          custom_word_obj = re.finditer(pattern, custom_scan_cont)
          for cutom_obj in custom_word_obj:
              line = cutom_obj.group().split(':')
              if line[2].strip() == 'null': continue
              TOTAL_WORDS_SCANNED += 1
              rows += '<tr><td class="word_width">%s</td>' % line[2].strip()
              rows += '<td class="line_width">%s:0</td>' % line[1]
              rows += '<td><pre>`%s` may be restricted by the organization<pre></td></tr>' % line[2].strip()
              CUSTOM_CONT.append(line[0])
          custom_scan_cont = re.sub(pattern, '', custom_scan_cont)
          #print('CUSTOM_CONT', CUSTOM_CONT)

    with open (component_name, "w+") as fw:
         fw.write(custom_scan_cont)
         fw.flush()

    if rows == "":
        rows += '<tr><td class="word_width" colspan=3><pre>No words found</pre></tr>'
    rows += "</table></div></td></tr>"

    content = tabe_row + header + rows
    return content

def remaining_words(file):
    global TOTAL_WORDS_SCANNED
    rows = ""
    content = ""
    component = file.replace('.alex', '.custom')
    with open (component, "r") as fr:
        cont = fr.read()
    if cont:
       files_list = re.findall(r'\.\/([a-z][^:]+?)\:', cont)
       files_list = list(set(files_list))
       for file in files_list:
           tabe_row = '<tr><td><h2>%s</h2><div class="file-diff"><table class="inner">' % file
           header = '<tr><th class="word_width">Word</th><th class="line_width">Line Number</th><th>Recommendation</th></tr>'
           pattern = '\.\/%s[^>]+?\\n' %file
           custom_word_obj = re.finditer(pattern, cont)
           for cutom_obj in custom_word_obj:
               line = cutom_obj.group().split(':')
               if line[2].strip() == 'null': continue
               TOTAL_WORDS_SCANNED += 1
               rows += '<tr><td class="word_width">%s</td>' % line[2].strip()
               rows += '<td class="line_width">%s:0</td>' % line[1]
               rows += '<td><pre>`%s` may be restricted by the organization<pre></td></tr>' % line[2].strip()
           if rows != "":
              rows += "</table></div></td></tr>"
              content += tabe_row + header + rows
           rows = ''
    return content


def main():
    """
    Generate the html report.
    """
    rows_cont = ''
    last_file_name = ''
    global TOTAL_FILES_SCANNED
    args = get_args()
    print ("Processing %s ....." %args)
    component_name = args.file
    files_generator = get_block_content(component_name)
    print ("File", files_generator)
    for file in files_generator:
        for file_name, words in file.items():
            if words == '':
                last_file_name = file_name
            else:
                rows_cont += html_row_gen(file_name, words, component_name)
                TOTAL_FILES_SCANNED += 1
        print('\n\n')
    rows_cont += remaining_words(component_name)

    print ("Read the html template file..")

    with open("alex_template.html", "r") as fp:
        html_tmpl_cont = fp.read()

    if eof and last_file_name == "":
        last_file_name = eof_file
    rows_cont += html_row_gen(last_file_name, eof, component_name)

    print (">>>>>>", rows_cont)
    if rows_cont == "":
        rows_cont = '<tr><td><h2></h2><div class="file-diff"><table class="inner">'
        rows_cont += '<tr><th class="word_width">Word</th><th class="line_width">Line Number</th><th>Recommendation</th></tr>'
        rows_cont += '<tr><td class="word_width" colspan=3><pre>No word found</pre></tr></table></div></td></tr>'

    if eof: TOTAL_FILES_SCANNED += 1

    component_name = component_name.replace('.alex', '')
    html_tmpl_cont = html_tmpl_cont.replace('##ROWCONTENT##', rows_cont)
    html_tmpl_cont = html_tmpl_cont.replace('##TotalWords##', str(TOTAL_WORDS_SCANNED))
    html_tmpl_cont = html_tmpl_cont.replace('##TotalFiles##', str(TOTAL_FILES_SCANNED))
    html_tmpl_cont = html_tmpl_cont.replace('##ComponentName##', component_name)
    html_tmpl_cont = html_tmpl_cont.replace('##ReportTime##', datetime.now().strftime("%Y-%m-%d %H:%M"))
    # print(TOTAL_FILES_SCANNED, TOTAL_WORDS_SCANNED)

    html_report_file = component_name + '.html'
    with open(html_report_file, 'w') as w_obj:
        w_obj.write(html_tmpl_cont)

    #print(html_tmpl_cont)

if __name__ == '__main__':
    main()
