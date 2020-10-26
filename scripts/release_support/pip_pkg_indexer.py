#!/usr/bin/python3
#
# Copyright (c) 2020 Seagate Technology LLC and/or its Affiliates
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


import os
import re
import shutil
from jinja2 import Template
from pathlib import Path

base = Path('/root/projects/python_deps')


index_html_template = """
<!DOCTYPE html>
<html lang="en">
    <head>
        <meta charset="utf-8">
        <title>Links for {{ pip_module }}</title>
    </head>
    <body>
    {%- for package in packages %}
        <a href="{{ package }}">{{ package }}</a></br>
    {%- endfor %}
    </body>
</html>

"""


def _valid_entities(content_list: list, verbose: bool = False):
    """Filter only the valid packages for processing"""
    
    if verbose:
        print(f"DEBUG: Filtering list: {content_list}")

    return filter(
        lambda filename: (
            "index.html" != filename
            or "pip_package_indexer.py" != filename
        )
        , content_list
    )


def create_index_file(dirname: Path, verbose: bool = False):
    """Create an index.html for given directory contents."""
    
    if verbose:
        print(f"DEBUG: Creating index file for: {dirname}")
    
    content_list = _valid_entities(os.listdir(dirname))

    # Render template for index.html
    template_obj = Template(index_html_template)
    index_contents = template_obj.render(pip_module=dirname.name, packages=content_list)
    if verbose:
        print(f"DEBUG: Index contents: {index_contents}")
    
    # Write index.html
    if verbose:
        print(f"DEBUG: Writing index contents to: {str(dirname / 'index.html')}")

    with open(dirname / "index.html", "w") as fd:
        fd.write(index_contents)


def _fetch_pkg_name(filename: Path, verbose: bool = False):
    """Identify Python package name from given filename."""

    if verbose:
        print(f"DEBUG: Identifying packagename for {filename}")

    pkg_name = None
    match_obj = re.search(r'.+(?=-\d)', filename.name)
    if match_obj:
        pkg_name = match_obj.group(0)

    if verbose:
        print(f"DEBUG: packagename: {pkg_name} :: filename: {filename}")

    return pkg_name, filename.name


def directory_builder(pkg_path: Path, verbose: bool = False):
    """Copy Python package file into it's dedicated directory."""

    if verbose:
        print(f"DEBUG: Building directory for {pkg_path}")

    pkg_name, filename = _fetch_pkg_name(pkg_path, verbose)
    
    # create directory
    pkg_dir = pkg_path.parent / f"{pkg_name}"
    os.makedirs(pkg_dir, exist_ok=True)
    
    # copy package to the dir
    if verbose:
        print(f"DEBUG: Moving package {str(pkg_path)} to {str(pkg_dir / filename)}")
    shutil.move(str(pkg_path), str(pkg_dir / filename))


def execute(base_dir: Path, verbose: bool = False):
    """Base path for the pip repository"""

    if verbose:
        print(f"DEBUG: Processing Base directory {base_dir}")

    for pkg in _valid_entities(os.listdir(base_dir)):
        if verbose:
            print(f"DEBUG: Processing package {pkg}")

        # move the packages to individual directories
        process_dir = base_dir / pkg

        if not process_dir.is_dir():
            directory_builder(process_dir, verbose)
        
        # Create index.html for each package dir
        pkg_name, filename = _fetch_pkg_name(process_dir, verbose)
        if (
            pkg_name
            and (base_dir / pkg_name).is_dir()
        ):
            create_index_file(base_dir / pkg_name, verbose)

    # Create index.html for root package dir
    create_index_file(base_dir, verbose)


if __name__ == "__main__":
    from optparse import OptionParser
    
    parser = OptionParser(
                usage = "%prog [options] base_dir",
                description = ("base_dir \t Base directory path, "
                "where Python PIP3 packages are stored."),
                version = "%prog 1.0"
            )
    parser.add_option("-v", "--verbose",
        action="store_true",
        dest="verbose",
        default=False,
        help="Control verbose (Debug statements)"
    )
    (options, args) = parser.parse_args()

    try:
        base_dir = Path(".")
        if len(args) and args[0]:
            base_dir = Path(args[0])
            if not base_dir.exists():
                raise FileNotFoundError(
                    f"Directory path {base_dir} does not exist. "
                    "Ensure an existing directory containing pip packages"
                    "is passed as input argument."
                )
        elif 0 == len(args):
            print("\nERROR: No args passed. The command expects one argument.\n")
            parser.print_help()
            exit(10)

        print(f"INFO: Processing dir {str(base_dir.resolve())}")
        execute(base_dir.resolve(), options.verbose)
    except KeyboardInterrupt:
        print("ERROR:  User has interrupted the execution with CTRL+C key sequence.")