Name:       cortx
Version:    release-version
Release:    1
Summary:    Most simple RPM package
License:    FIXME

Requires:   builddep
%description
This is my first RPM package, which does nothing.

%prep
# we have no source, so nothing here

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
# let's skip this for now
