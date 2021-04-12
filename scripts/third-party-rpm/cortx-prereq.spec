Name:       cortx-prereq
Version:    %{_cortx_prereq_version}
Release:    %{_release_version}_%{_git_hash}_%{?dist:el7}
Summary:    CORTX pre-requisite package
License:    Seagate

Requires:  PyYAML attr compat-lua-libs consul >= 1.7.0, consul < 1.10.0 corosync elasticsearch-oss == 6.8.8 facter fence-agents-ipmilan findutils gdb genders gflags glog haproxy hdparm == 9.43 hiredis ipmitool == 1.8.18 jq kibana-oss == 6.8.8 libaio libedit libxml2 libyaml log4cxx_cortx log4cxx_cortx-devel lshw == B.02.18 openldap-clients openldap-servers openssl pacemaker pcs perl perl(Config::Any) perl-DateTime perl-File-Which perl-List-MoreUtils perl-MCE perl-Sereal perl-Try-Tiny perl-YAML-LibYAML perl-autodie pkgconfig python-magic python3-pip python36 == 3.6.8 python36-PyYAML python36-dbus == 1.2.4 python36-gobject == 3.22.0 python36-ldap python36-paramiko == 2.1.1 python36-pika python36-pip python36-ply python36-psutil == 5.6.7 rabbitmq-server == 3.8.9 rsync rsyslog ruby rubygem-net-ssh selinux-policy shadow-utils == 2:4.6-5.el7 smartmontools == 1:7.0-2.el7 sos stats_utils statsd systemd-python36 == 1.0.0 sysvinit-tools udisks2 == 2.8.4 yaml-cpp 
Source:    %{name}-%{version}-%{_git_hash}.tgz

%description
CORTX Depenedecny Package

%global debug_package %{nil}

%pre
if [ ! -f "/etc/pip.conf" ]; then
   echo "ERROR:/etc/pip.conf is not configured with custom python repo. Exiting.."
   echo "Please follow steps mentioned at https://github.com/Seagate/cortx-prvsnr/wiki/Deploy-VM-Hosted-Repo#production-environment"
   exit 1
fi


%prep
%setup -n %{name}-%{version}-%{_git_hash}
rm -rf %{buildroot}

%build

%install
mkdir -p %{buildroot}/opt/seagate/cortx/python-deps
cp -R python-requirements.txt %{buildroot}/opt/seagate/cortx/python-deps

%files
/opt/seagate/cortx/python-deps/python-requirements.txt

%post
echo -e "\n Installing CORTX prerequisite Python packages. \n"
pip3 install -r /opt/seagate/cortx/python-deps/python-requirements.txt

%clean
rm -rf %{buildroot}

%changelog
# TODO
