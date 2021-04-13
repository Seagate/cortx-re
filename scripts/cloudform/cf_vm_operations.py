#
# Copyright (c) 2020 Seagate Technology LLC and/or its Affiliates
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

import requests
import argparse
import json
import sys
import time
# from redis import Redis
# from rq import Queue

from requests.packages import urllib3
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry
from requests.auth import HTTPBasicAuth

# Disable insecure-certificate-warning message
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# Global Variable declaration
MAX_RETRY = 2
MAX_RETRY_FOR_SESSION = 2
BACK_OFF_FACTOR = 0.3
TIME_BETWEEN_RETRIES = 1000
ERROR_CODES = (500, 502, 504)
ITER_COUNT = 20


def requests_retry_session(retries=MAX_RETRY_FOR_SESSION,
                           back_off_factor=BACK_OFF_FACTOR,
                           status_force_list=ERROR_CODES,
                           session=None):
    """
    Create a session to process SSC API request
    Parameters:
    ----------
        status_force_list (list): Error codes list to retry
        back_off_factor (float): Back-off factor
        retries (float): Maximum retry per session
        session (object): None
    Returns:
    -------
       session (object): Session Object to process REST API
    """
    session = session
    retry = Retry(total=retries, read=retries, connect=retries,
                  backoff_factor=back_off_factor,
                  status_forcelist=status_force_list,
                  method_whitelist=frozenset(['GET', 'POST']))
    adapter = HTTPAdapter(max_retries=retry)
    session.mount('http://', adapter)
    session.mount('https://', adapter)
    return session


def get_args():
    """
    Read the parameter from commandline

    Returns:
    parser (list): Parsed commandline parameter
    """
    # Create the parser
    parser = argparse.ArgumentParser()
    # Add the arguments
    parser.add_argument('--action', '-a', choices=['create_vm', 'list_vm_snaps', 'revert_vm_snap',
                                                   'retire_vm', 'get_vm_info'], required=True,
                        help="Perform the Operation")
    parser.add_argument('--token', '-t', help="Token for API Authentication")
    parser.add_argument('--fqdn', '-f', choices=['ssc-cloud.colo.seagate.com'],
                        default="ssc-cloud.colo.seagate.com", help="SSC hostname")
    parser.add_argument('--service_template', '-s', help="Service Template ID for VM creation")
    parser.add_argument('--service_id', '-i', help="Service Template ID for VM creation")
    parser.add_argument('--host', '-v', help="SSC VM name")
    parser.add_argument('--extra_disk_count', '-d', default=1, choices=range(1, 12), help="Extra disk count of the VM")
    parser.add_argument('--extra_disk_size', '-f', default=25, choices=[25, 50, 75, 100], help="Extra disk size of the VM")
    parser.add_argument('--cpu', '-c', default=1, choices=[1, 2, 4, 8], help="Number of Core for VM")
    parser.add_argument('--memory', '-m', default=4096, choices=[4096, 8192, 16384], help="VM Memory")
    parser.add_argument('--snap_id', '-n', help="Snap ID of the VM")
    parser.add_argument('--user', '-u', help="GID of the user for SSC Auth")
    parser.add_argument('--password', '-p', help="Password of the user for SSC Auth")
    # Execute the parse_args() method and return
    return parser.parse_args()


class VMOperations:
    """
    This will help to reduce manual workload required to create vm's for deployment and other vm related testing.

    Attributes
    ----------
    parameters (list) : Commandline Inputs

    Methods
    -------
    create_vm(): Create the SSC VM for the given service template
    get_vm_info(): Get the information about the VM
    retire_service(): Retires given service
    retire_vm(): Retires the given VM
    list_vm_snaps(): List all the snapshots for the given VM
    get_catalog_id(): Get the service catalog for the given VM
    revert_vm_snap(): Revert the snapshot for the given VM
    stop_vm(): Stop the operation for given VM
    """

    def __init__(self, parameters):
        self.args = parameters
        self.url = ""
        self.method = "GET"
        self.payload = {}
        self.headers = {'content-type': 'application/json'}
        self.session = requests_retry_session(session=requests.Session())

        if not parameters.token:
            _url = 'https://%s/api/auth' % parameters.fqdn
            _response = self.session.get(_url, auth=HTTPBasicAuth(parameters.user, parameters.password), verify=False)
            self.args.token = _response.json()['auth_token']

    def execute_request(self):
        try:

            if self.method == "POST":
                r = self.session.post(self.url, data=json.dumps(self.payload), headers=self.headers, verify=False)
            else:
                r = self.session.get(self.url, data=json.dumps(self.payload), headers=self.headers, verify=False)
        except requests.exceptions.RequestException as e:
            raise SystemExit(e)
        return r.json()

    def check_status(self, _response):
        self.url = _response['task_href']
        self.method = "GET"
        self.payload = ""
        _count = 0
        _res = ''
        while _count < ITER_COUNT:
            time.sleep(30)
            _res = self.execute_request()
            _rss_state = _res['state']
            if _rss_state == "Finished":
                print(json.dumps(_res, indent=4, sort_keys=True))
                break
            else:
                print("Checking the VM status again...")
                if _count == ITER_COUNT:
                    print('The request has been processed, but response state is not matched with expectation')
                    sys.exit()
            _count += 1
        return _res

    def get_catalog_id(self):
        self.method = "GET"
        self.payload = ""
        self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
        self.url = "https://%s/api/service_templates/%s" % (self.args.fqdn, self.args.service_template)
        # Process the request
        return self.execute_request()

    def create_vm(self):
        service_template_resp = self.get_catalog_id()
        service_catalog_id = service_template_resp['service_template_catalog_id']
        self.method = "POST"
        self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
        self.url = "https://%s/api/service_catalogs/%s/service_templates/%s" \
                   % (self.args.fqdn, service_catalog_id, self.args.service_template)
        self.payload = {
            "action": "order",
            "resource": {
                "href": "https://%s/api/service_templates/%s" % (self.args.fqdn, self.args.service_template),
                "dialog_check_box_1": "t",
                "extra_disk_count": self.args.extra_disk_count,
                "extra_disk_size": self.args.extra_disk_size,
                "option_0_vm_memory": self.args.memory,
                "option_0_cores_per_socket": self.args.cpu,
                "dialog_share_vms_disks": "t"
            }
        }

        # Process the request
        _response = self.execute_request()
        if _response['status'] == "Ok":
            self.method = "GET"
            _service_req_url = _response['href']
            self.url = "%s??expand=request_tasks" % _service_req_url
            self.payload = ""
            print("Creating the VM might take time...")
            _count = 0
            while _count < ITER_COUNT:
                time.sleep(60)
                vm_status_res = self.execute_request()
                _vm_state = vm_status_res['request_state']
                _count += 1
                if _vm_state == "finished":
                    print("Expected VM state is matched with current VM state")
                    _vm_message = vm_status_res['message']
                    print("VM message: %s" % _vm_message)
                    print(json.dumps(vm_status_res, indent=4, sort_keys=True))
                    break
                else:
                    print("Expected VM state is finished, but current state is %s..." % _vm_state)
                    if _count == ITER_COUNT:
                        print('VM has ordered successfully, but VM state is not matched with expectation..')
                        sys.exit()
        else:
            print("Failed to process the VM request..%s" % _response)
        return _response

    def get_vm_info(self):
        self.payload = ""
        self.method = "GET"
        self.url = "https://%s/api/vms?expand=resources&filter%%5B%%5D=name='%s'" \
                   % (self.args.fqdn, self.args.host)
        self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
        return self.execute_request()

    def retire_vm(self):
        _get_vm_info = self.get_vm_info()
        _response = ""
        if _get_vm_info['resources'][0]['retirement_state'] != "retired":
            _vm_id = _get_vm_info['resources'][0]['id']
            self.method = "POST"
            self.url = "https://%s/api/vms/%s" % (self.args.fqdn, _vm_id)
            self.payload = {
                "action": "retire"
            }
            self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
            # Process the request
            _response = self.execute_request()
            print("Retiring the VM might take time...")
            _count = 0
            while _count < ITER_COUNT:
                time.sleep(30)
                _vm_info = self.get_vm_info()
                _vm_state = _vm_info['resources'][0]['retirement_state']
                if _vm_state == "retired":
                    print("Matched current VM state and expected VM state")
                    print("VM has been retired successfully....")
                    print(json.dumps(_vm_info, indent=4, sort_keys=True))
                    break
                else:
                    print("Current VM state is %s, expected VM state is finished.." % _vm_state)
                    if _count == ITER_COUNT:
                        print('VM retire request has processed, but VM state is unexpected..')
                        sys.exit()
                _count += 1
        else:
            print("The VM already is in retired state...")
            sys.exit()
        return _response

    def list_vm_snaps(self):
        _vm_info = self.get_vm_info()
        _vm_id = _vm_info['resources'][0]['id']
        self.url = "https://%s/api/vms/%s/snapshots" % (self.args.fqdn, _vm_id)
        self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
        # Process the request
        return self.execute_request()

    def stop_vm(self, vm_id):
        self.method = "POST"
        self.url = "https://%s/api/vms/%s" % (self.args.fqdn, vm_id)
        self.payload = {
            "action": "stop"
        }
        self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
        # Process the request
        _response = self.execute_request()
        if _response['success']:
            print("Stopping the VM might take time...")
            _res_stop = self.check_status(_response)
        return _response

    def start_vm(self, vm_id):
        self.method = "POST"
        self.url = "https://%s/api/vms/%s" % (self.args.fqdn, vm_id)
        self.payload = {
            "action": "start"
        }
        self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
        # Process the request
        _response = self.execute_request()
        if _response['success']:
            print("Starting the VM might take time...")
            _res_start = self.check_status(_response)
        return _response

    def revert_vm_snap(self, _response=''):
        _vm_info = self.get_vm_info()
        _vm_state = _vm_info['resources'][0]['power_state']
        _vm_id = _vm_info['resources'][0]['id']
        _stop_res = ""

        # Stop the VM before snapshot revert
        if _vm_state == "on":
            _stop_res = self.stop_vm(_vm_id)
            if _stop_res:
                time.sleep(30)
                _vm_info = self.get_vm_info()
                _vm_state = _vm_info['resources'][0]['power_state']
                print("VM has been stopped successfully. VM status is %s.." % _vm_state)
        else:
            print("VM is already stopped...")

        if _stop_res or _vm_state == "off":
            print("Revert the VM snapshots...")
            self.method = "POST"
            self.payload = {"action": "revert"}
            self.url = "https://%s/api/vms/%s/snapshots/%s" % (self.args.fqdn, _vm_id, self.args.snap_id)
            self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
            # Process the request
            _response = self.execute_request()
            if _response["success"]:
                _revert_res = self.check_status(_response)
                if _revert_res['state'] == "Finished":
                    print("Revert message: %s" % _revert_res['message'])
                    print(json.dumps(_revert_res, indent=4, sort_keys=True))
                    # Start the VM after snapshot revert
                    _start_res = self.start_vm(_vm_id)
                    if _start_res:
                        print("Started the VM after snapshot revert")
                        print(json.dumps(_start_res, indent=4, sort_keys=True))
                    else:
                        print("Failed to start the VM after revert...")
                else:
                    print("Failed to revert the VM...")
                    print("Response: %s" % _revert_res)
            else:
                print("Failed to process the revert API request...")
        return _response


def main():
    args = get_args()
    if not (args.user and args.password) and not args.token:
        sys.exit("Specify either token/password for SSC Auth...")

    result = {}
    # Create a VM operations object
    vm_object = VMOperations(args)
    print("Processing the %s....." % args.action)

    # Check validation for each actions
    if args.action == "create_vm":
        if args.service_template:
            result = vm_object.create_vm()
    elif args.action == "retire_vm":
        if args.host:
            result = vm_object.retire_vm()
    elif args.action == "get_vm_info":
        if args.host:
            result = vm_object.get_vm_info()
    elif args.action == "list_vm_snaps":
        if args.host:
            result = vm_object.list_vm_snaps()
    elif args.action == "revert_vm_snap":
        if args.snap_id and args.host:
            result = vm_object.revert_vm_snap()

    if result:
        print("VM operation %s request has been polled successfully....." % args.action)
        print(json.dumps(result, indent=4, sort_keys=True))
    else:
        print("Please check the command-line parameter")


if __name__ == '__main__':
    main()
