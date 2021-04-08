#!/usr/bin/env python3
# -*- coding: utf-8 -*-
#######################################################################
# Author                : Venkatesh K
# Date                  : 08-04-2021
# Description           : Automation for Cloudforms SSC VM Operations
# Usage                 : Refer the get_args function or documentation
#######################################################################

import requests
import argparse
import json
import sys
import time

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
    parser.add_argument('--action', '-a', required=True, help="Perform the Operation")
    parser.add_argument('--token', '-t', help="Token for API Authentication")
    parser.add_argument('--fqdn', '-f', default="ssc-cloud.colo.seagate.com", help="SSC hostname")
    parser.add_argument('--service_template', '-s', help="Service Template ID for VM creation")
    parser.add_argument('--service_id', '-i', help="Service Template ID for VM creation")
    parser.add_argument('--host', '-v', help="SSC VM name")
    parser.add_argument('--cpu', '-c', default=4, help="Number of Core for VM")
    parser.add_argument('--memory', '-m', default=8192, help="VM Memory")
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
            _url = 'https://ssc-cloud.colo.seagate.com/api/auth'
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
        self.url = "https://%s/api/service_catalogs/%s/service_templates/%s" \
                   % (self.args.fqdn, service_catalog_id, self.args.service_template)
        self.payload = {
            "action": "order",
            "resource": {
                "href": "https://%s/api/service_templates/%s" % (self.args.fqdn, self.args.service_template),
                "dialog_check_box_1": "t",
                "extra_disk_count": "2",
                "extra_disk_size": "50",
                "option_0_vm_memory": self.args.memory,
                "option_0_cores_per_socket": self.args.cpu,
                "dialog_share_vms_disks": "t"
            }
        }

        # Process the request
        return self.execute_request()

    def get_vm_info(self):
        self.payload = ""
        self.method = "GET"
        self.url = "https://%s/api/vms?expand=resources&filter%%5B%%5D=name='%s'" \
                   % (self.args.fqdn, self.args.host)
        self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
        return self.execute_request()

    def retire_service(self):
        self.method = "POST"
        self.url = "https://%s/api/services/%s" % (self.args.fqdn, self.args.service_id)
        self.payload = {
            "action": "retire"
        }
        self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
        # Process the request
        return self.execute_request()

    def retire_vm(self):
        _get_vm_info = self.get_vm_info()
        _vm_id = _get_vm_info['resources'][0]['id']
        self.method = "POST"
        self.url = "https://%s/api/vms/%s" % (self.args.fqdn, _vm_id)
        self.payload = {
            "action": "retire"
        }
        self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
        # Process the request
        return self.execute_request()

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
        return self.execute_request()

    def start_vm(self, vm_id):
        self.method = "POST"
        self.url = "https://%s/api/vms/%s" % (self.args.fqdn, vm_id)
        self.payload = {
            "action": "start"
        }
        self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
        # Process the request
        return self.execute_request()

    def revert_vm_snap(self, response=None):
        _vm_info = self.get_vm_info()
        _vm_state = _vm_info['resources'][0]['power_state']
        _vm_id = _vm_info['resources'][0]['id']

        # Stop the VM before snapshot revert
        if _vm_state == "on":
            _stop_res = self.stop_vm(_vm_id)
            print("Response for stop VM request: ")
            print(json.dumps(_stop_res, indent=4, sort_keys=True))

            print("Stopping the VM might take time...")
            _count = 0
            while _count < 15:
                time.sleep(30)
                _vm_info = self.get_vm_info()
                _vm_state = _vm_info['resources'][0]['power_state']
                if _vm_state == "off":
                    print("VM has been stopped successfully....")
                    print(json.dumps(_vm_info, indent=4, sort_keys=True))
                    break
                else:
                    print("VM state is still on, so iterating again...")
                _count += 1
        else:
            print("VM is already stopped...")

        if _vm_state == "off":
            print("Revert the VM snapshots...")
            self.method = "POST"
            self.payload = {"action": "revert"}
            self.url = "https://%s/api/vms/%s/snapshots/%s" % (self.args.fqdn, _vm_id, self.args.snap_id)
            self.headers = {'content-type': 'application/json', 'X-Auth-Token': self.args.token}
            # Process the request
            response = self.execute_request()
            time.sleep(60)
            # Start the VM after snapshot revert
            _start_res = self.start_vm(_vm_id)
            print("Starting the VM after snapshot revert")
            print(json.dumps(_start_res, indent=4, sort_keys=True))
        return response


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
    elif args.action == "retire_service":
        if args.service_id:
            result = vm_object.retire_service()
    elif args.action == "revert_vm_snap":
        if args.snap_id:
            result = vm_object.revert_vm_snap()

    if result:
        print("VM operation %s has been processed successfully....." % args.action)
        print(json.dumps(result, indent=4, sort_keys=True))
    else:
        print("Please check the VM action. It should be one of "
              "[create_vm, list_vm_snaps, revert_vm_snap, retire_vm, get_vm_info]")


if __name__ == '__main__':
    main()
