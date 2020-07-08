Name:       cortx
Version:    release-version
Release:    1
Summary:    Most simple RPM package
License:    Seagate LLC

Requires:   builddep
%description
This meta rpm used to install/update the cortx rpms.

%prep
# TODO

%build
cat > meta-rpm.sh <<EOF
#!/usr/bin/bash
echo Dependencies Installed
EOF

%install
mkdir -p %{buildroot}/usr/bin/
install -m 755 meta-rpm.sh %{buildroot}/usr/bin/meta-rpm.sh

%files
/usr/bin/meta-rpm.sh

%changelog
# TODO
