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

import re
import os
import sys
import glob
import pickle
import shutil
from datetime import datetime

alex_report_status = {}
date = datetime.today().strftime('%Y-%m-%d')
alex_report_status[date] = {}

current_year = datetime.now().strftime('%Y')
current_month = datetime.now().strftime('%B')
current_day = datetime.now().strftime('%d')

#print (current_month, current_year, current_day)

alex_path = os.environ['ALEX_PATH']
directory = os.path.join(alex_path, current_year, current_month, current_day)

if not os.path.exists(directory):
    os.makedirs(directory)

html_files = glob.glob("cortx*.html")
#print (html_files)

for file in html_files:
    print ("Copy the %s into ci-storage" %file)
    shutil.copy(file, directory)

    with open(file,'r') as f_obj:
       cont = f_obj.read()
    counts = dict()
    result = re.search(r'Component\s*Name</td><td>([^>]+?)<', cont)
    repo = result.group(1)

    pattern = re.compile(r'class="word_width">([^>]+)<')

    for match in pattern.finditer(cont):
       word = match.group(1)
       word = word.replace("`", "")
       if word in counts:
          counts[word] += 1
       else:
          counts[word] = 1
    alex_report_status[date][repo] = counts

print ("Updating the pickle object..")
with open('./cortx/metrics/pickles/alex.pickle', "wb") as f:
    pickle.dump(alex_report_status, f)

with open('./cortx/alex.pickle', "rb") as f:
    print ("Updated the pickle file successfully:\n %s" %pickle.load(f))


#print (alex_report_status)
