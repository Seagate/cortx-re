# Updating the spec template file

## Download the ceph.spec.template in raw format

`wget -O $release_dir/SPECS/ceph.spec https://raw.githubusercontent.com/Seagate/cortx-re/main/scripts/rgw/ceph/ceph.spec.template`

## Change to cortx-rgw repo and update the downloaded spec file

```
pushd cortx-rgw
    version=$(git describe --long --match 'v*' | sed 's/^v//')
    if expr index $version '-' > /dev/null; then
        rpm_version=$(echo $version | cut -d - -f 1-1)
        rpm_release=$(echo $version | cut -d - -f 2- | sed 's/-/./')
    else
        rpm_version=$version
        rpm_release=0
    fi
    for spec in $release_dir/SPECS/ceph.spec; do
        cat $spec |
            sed "s/@PROJECT_VERSION@/$rpm_version/g" |
            sed "s/@RPM_RELEASE@/$rpm_release/g" |
            sed "s/@TARBALL_BASENAME@/ceph-$version/g" > `echo $spec | sed 's/.in$//'`
    done
popd
```

As per template, ceph.spec file is now updated at line no.: 146, 147, 162 and 676.
