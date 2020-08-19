set -x
pushd  scripts/cortx_metarpm/
`curl http://cortx-storage.colo.seagate.com/releases/eos/github/$release_folder/$OS_version/$build_num/prod/ | sed -n "/rpm/p" | cut -d'"' -f 2 > abc.txt`
rpm --import http://cortx-storage.colo.seagate.com/releases/eos/github/$release_folder/$OS_version/$build_num/prod/RPM-GPG-KEY-Seagate
rm -rf output.txt
for i in $(cat abc.txt)
do
rpm_name=$(rpm -qp http://cortx-storage.colo.seagate.com/releases/eos/github/$release_folder/$OS_version/$build_num/prod/$i --qf '%{NAME}')
rpm_version=$(rpm -qp http://cortx-storage.colo.seagate.com/releases/eos/github/$release_folder/$OS_version/$build_num/prod/$i --qf '%{VERSION}')
rpm_release=$(rpm -qp http://cortx-storage.colo.seagate.com/releases/eos/github/$release_folder/$OS_version/$build_num/prod/$i --qf '%{RELEASE}')
echo $rpm_name = $rpm_version-$rpm_release >> output.txt
done
builddep=$(cat output.txt | tr '\n' ' ')
sed -i "s/release-name/$release_type/g" cortx.spec
sed -i "s/builddep/$builddep/g" cortx.spec
if [[ "${release_type}" == "Beta" || "${release_type}" == "Rename"  ]];then
relversion=$(echo $build_num | cut -d"-" -f 3 )
sed -i "s/release-version/$relversion/" cortx.spec
else
relversion=$build_num
sed -i "s/release-version/$relversion/" cortx.spec
fi
cat cortx.spec
rpmbuild -bb cortx.spec
rpm_location=/root/rpmbuild/RPMS/x86_64/$release_type-$relversion-1.x86_64.rpm
