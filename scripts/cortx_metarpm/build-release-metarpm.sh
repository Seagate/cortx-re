#!/bin/bash
set -x
release_type="CORTX-1.0.0"
pushd  scripts/cortx_metarpm/
metainfo=$(curl "http://cortx-storage.colo.seagate.com/releases/eos/github/$release_folder/$OS_version/$build_num/prod/" | sed -n "/rpm/p" | cut -d'"' -f 2 | tr ' ' '\n')
rpm --import "http://cortx-storage.colo.seagate.com/releases/eos/github/$release_folder/$OS_version/$build_num/prod/RPM-GPG-KEY-Seagate"
rm -rf dependencies.txt
for i in $metainfo
do
rpm_name=$(rpm -qp "http://cortx-storage.colo.seagate.com/releases/eos/github/$release_folder/$OS_version/$build_num/prod/$i" --qf '%{NAME}')
rpm_version=$(rpm -qp "http://cortx-storage.colo.seagate.com/releases/eos/github/$release_folder/$OS_version/$build_num/prod/$i" --qf '%{VERSION}')
rpm_release=$(rpm -qp "http://cortx-storage.colo.seagate.com/releases/eos/github/$release_folder/$OS_version/$build_num/prod/$i" --qf '%{RELEASE}')
echo "$rpm_name" = "$rpm_version"-"$rpm_release" >> dependencies.txt
done
builddep=$( < dependencies.txt tr '\n' ' ')
sed -i "s/release-name/$release_type/g" cortx.spec
sed -i "s/builddep/$builddep/g" cortx.spec
relversion=$build_num
sed -i "s/release-version/$relversion/" cortx.spec
rpmbuild -bb cortx.spec
rpm_location=/root/rpmbuild/RPMS/x86_64/$release_type-$relversion-1.x86_64.rpm
