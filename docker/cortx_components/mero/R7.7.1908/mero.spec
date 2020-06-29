# feature flags
%bcond_with ut
%bcond_with debuginfo_sources
%bcond_with cassandra

# configure options
%define with_linux  %( test -n "$kernel_src" && echo "--with-linux=$kernel_src" )
%define with_lustre %( test -n "$lustre_src" && echo "--with-lustre=$lustre_src" )

# kernel version.
%define raw_kernel_ver %(
                          if test -n "$kernel_src"; then
                              basename $(readlink -f "$kernel_src")
                          else
                              uname -r
                          fi
                        )

%define kernel_ver %( echo %{raw_kernel_ver} | tr - _ | sed -e 's/\.debug$//' -e 's/\.%{_arch}$//' -e 's/\.%{?dist:-el7}$//' -e 's/\.el7$//'  )
%define kernel_ver_requires %( echo %{raw_kernel_ver} | sed -e 's/\.debug$//' -e 's/\.x86_64$//' )

# build number
%define build_num  %( test -n "$build_number" && echo "$build_number" || echo 1 )

%define lustre         lustre-client
%define lustre_devel   lustre-client-devel

# workaround issue with mero-debuginfo package, which injects incorrect
# dependencies on itself into mero package when internal deps generator is used;
# that prevents a correct installation of any of the built mero packages without
# using '--no-deps' option
%global _use_internal_dependency_generator 0
%define _use_internal_dependency_generator 0

# configure options
%define  configure_release_opts     --enable-release --disable-avx --enable-force-ssse3 --with-trace-kbuf-size=256 --with-trace-ubuf-size=64
%define  configure_ut_opts          --enable-dev-mode --disable-altogether-mode
%if %{with cassandra}
%define  configure_cassandra_opts   --with-cassandra
%endif

%if %{with ut}
%define  configure_opts  %{configure_ut_opts} %{?configure_cassandra_opts}
%else
%define  configure_opts  %{configure_release_opts} %{?configure_cassandra_opts}
%endif

Name:           @PACKAGE@
Version:        @PACKAGE_VERSION@
Release:        %{build_num}_@GIT_REV_ID_RPM@_%{kernel_ver}%{?dist}
Summary:        Mero filesystem and development libraries
Group:          System Environment/Base
License:        Xyratex
Source:         %{name}-%{version}.tar.gz
BuildArch:      x86_64
ExcludeArch:    i686
Provides:       %{name}-libs = %{version}-%{release}
Provides:       %{name}-modules = %{version}-%{release}

BuildRequires:  automake
BuildRequires:  autoconf
BuildRequires:  libtool
BuildRequires:  make
BuildRequires:  gcc
BuildRequires:  gcc-c++
BuildRequires:  gccxml
BuildRequires:  glibc-headers
BuildRequires:  asciidoc
BuildRequires:  libyaml-devel
BuildRequires:  libaio-devel
BuildRequires:  perl
BuildRequires:  perl-XML-LibXML
BuildRequires:  perl-List-MoreUtils
BuildRequires:  perl-File-Find-Rule
BuildRequires:  perl-IO-All
BuildRequires:  perl-File-Slurp
BuildRequires:  perl-YAML-LibYAML
BuildRequires:  kernel-devel = %{kernel_ver_requires}
BuildRequires:  %{lustre_devel}
BuildRequires:  libuuid-devel
BuildRequires:  binutils-devel
BuildRequires:  python36-ply
BuildRequires:  perl-autodie
BuildRequires:  systemd-devel
%if %{with cassandra}
BuildRequires:  libcassandra
BuildRequires:  libuv
%endif

Requires:       kernel = %{kernel_ver_requires}
Requires:       %{lustre}
Requires:       libaio
Requires:       libyaml
Requires:       genders
Requires:       sysvinit-tools
Requires:       attr
Requires:       perl
Requires:       perl-YAML-LibYAML
Requires:       perl-DateTime
Requires:       perl-File-Which
Requires:       perl-List-MoreUtils
Requires:       perl-autodie
Requires:       perl-Try-Tiny
Requires:       perl-Sereal
Requires:       perl-MCE
Requires:       ruby
Requires:       facter
Requires:       rubygem-net-ssh
Requires:       python36-ply
Requires(pre):  shadow-utils
Requires(post): findutils

%description
Mero filesystem runtime environment and servers.

%package devel
Summary: Mero include headers
Group: Development/Kernel
Provides: %{name}-devel = %{version}-%{release}
Requires: binutils-devel
Requires: libyaml-devel
Requires: libaio-devel
Requires: libuuid-devel
Requires: pkgconfig
Requires: systemd-devel
Requires: glibc-headers
Requires: kernel-devel = %{kernel_ver_requires}
Requires: %{lustre_devel}


%description devel
This package contains the headers required to build external
applications that use Mero libraries.

%if %{with ut}
%package tests-ut
Summary: Mero unit tests
Group: Development/Kernel
Conflicts: %{name}

%description tests-ut
This package contains Mero unit tests (for kernel and user space).
%endif # with ut

%prep
%setup -q

%build
%configure %{with_linux} %{with_lustre} %{configure_opts}
make %{?_smp_mflags}

%install
rm -rf %{buildroot}
%if %{with ut}

make DESTDIR=%{buildroot} install-tests

find %{buildroot} -name m0mero.ko -o -name m0ut.ko -o -name m0loop-ut.ko \
    -o -name m0gf.ko | sed -e 's#^%{buildroot}##' > tests-ut.files

find %{buildroot} \
        -name m0ut \
        -o -name m0ut-isolated \
        -o -name m0ub \
        -o -name m0run \
        -o -name m0gentestds \
        -o -name 'm0kut*' \
        -o -name 'libtestlib*.so*' \
        -o -name 'libmero*.so*' \
        -o -name 'libgf_complete*.so*' |
    sed -e 's#^%{buildroot}##' >> tests-ut.files

sort -o tests-ut.files tests-ut.files
find %{buildroot} -type f | sed -e 's#^%{buildroot}##' | sort |
    comm -13 tests-ut.files - | sed -e 's#^#%{buildroot}#' > tests-ut.exclude
xargs -a tests-ut.exclude rm -rv

%else

make DESTDIR=%{buildroot} install
find %{buildroot} -name 'm0ff2c' -o \
                  -name 'm0gccxml2xcode*' |
    sed -e 's#^%{buildroot}##' -e 's/\.1$/.1.gz/' > devel.files
find %{buildroot} -name 'libmero-xcode-ff2c*.so*' | sed -e 's#^%{buildroot}##' >> devel.files
find %{buildroot} -name '*.la' | sed -e 's#^%{buildroot}##' >> devel.files
find %{buildroot}%{_includedir} | sed -e 's#^%{buildroot}##' >> devel.files
find %{buildroot}%{_libdir} -name mero.pc | sed -e 's#^%{buildroot}##' >> devel.files
mkdir -p %{buildroot}%{_localstatedir}/mero

%endif # with ut

# Remove depmod output - it is regenerated during %post
if [ -e %{buildroot}/lib/modules/%{raw_kernel_ver}/modules.dep ]; then
	rm %{buildroot}/lib/modules/%{raw_kernel_ver}/modules.*
fi


%files
%if !%{with ut}
%doc AUTHORS README NEWS ChangeLog COPYING
%{_bindir}/*
%{_sbindir}/*
%{_libdir}/*
%{_libexecdir}/mero/*
%{_exec_prefix}/lib/*
%{_datadir}/*
%{_mandir}/*
%attr(0770, mero, mero) %{_localstatedir}/mero
#%attr(0775, mero, mero) %{_sysconfdir}/mero
#%config %attr(0664, mero, mero) %{_sysconfdir}/mero/*
%config %attr(0664, mero, mero) %{_sysconfdir}/sysconfig/*
%{_sysconfdir}/systemd/system/*
%{_sysconfdir}/security/limits.d/90-mero.conf
/lib/modules/*/kernel/fs/mero/*
%exclude %{_bindir}/m0gentestds
%exclude %{_bindir}/m0gccxml2xcode
%exclude %{_bindir}/m0kut*
%exclude %{_bindir}/m0ut*
%exclude %{_bindir}/m0ub
%exclude %{_bindir}/m0ff2c
%exclude %{_sbindir}/m0run
%exclude %{_libdir}/*.la
%exclude %{_libdir}/libmero-ut*
%exclude %{_libdir}/libtestlib*
%exclude %{_libdir}/libmero-xcode-ff2c*
%exclude %{_libdir}/pkgconfig/
%exclude %{_mandir}/**/m0gccxml2xcode*
%exclude /lib/modules/*/kernel/fs/mero/clovis*
%exclude /lib/modules/*/kernel/fs/mero/m0lnet*
%exclude /lib/modules/*/kernel/fs/mero/m0net*
%exclude /lib/modules/*/kernel/fs/mero/m0rpc*
%exclude /lib/modules/*/kernel/fs/mero/m0ut*
%endif # with ut

%if %{with ut}
%files tests-ut -f tests-ut.files
%else
%files devel -f devel.files
%endif

# See 'post' section comment about '-e' argument.
%pre -e

# Guidelings for user/group creation in Fedora:
#   https://fedoraproject.org/wiki/Packaging:UsersAndGroups?rd=Packaging/UsersAndGroups
getent group  mero >/dev/null || groupadd --system mero
getent passwd mero >/dev/null || useradd --system --shell /sbin/nologin \
    --no-user-group --gid mero --home-dir / --comment 'Mero daemon' mero

# The '-e' argument enables runtime macro expansion for this particular script.
# Also note that macros have to be escaped using %% and not %, otherwise it
# would be expanded at build-time.
# See http://rpm.org/user_doc/scriptlet_expansion.html for more info.
%post -e
/sbin/depmod -a
systemctl daemon-reload

if [ x%%{?no_trace_logs} != x ] ; then
    /bin/sed -i -r -e "s/(MERO_TRACED_KMOD=)yes/\1no/" /etc/sysconfig/mero
    /bin/sed -i -r -e "s/(MERO_TRACED_M0D=)yes/\1no/" /etc/sysconfig/mero
fi

# when doing an upgrade
if [ $1 -eq 2 ] ; then
   echo "Upgrading %{_localstatedir}/mero group and permissions"
   find %{_localstatedir}/mero ! -group mero -exec chown :mero '{}' \; -exec chmod g+w,o-rwx '{}' \;
fi

%postun
/sbin/depmod -a
systemctl daemon-reload

%if %{with ut}

%post tests-ut
/sbin/depmod -a

%postun tests-ut
/sbin/depmod -a

%endif # with ut

# Remove source code from debuginfo package.
%if !%{with debuginfo_sources}
%define __debug_install_post \
  %{_rpmconfigdir}/find-debuginfo.sh %{?_missing_build_ids_terminate_build:--strict-build-id} %{?_find_debuginfo_opts} "%{_builddir}/%{?buildsubdir}"; \
  rm -rf "${RPM_BUILD_ROOT}/usr/src/debug"; \
  mkdir -p "${RPM_BUILD_ROOT}/usr/src/debug/%{name}-%{version}"; \
%{nil}
%endif # debuginfo_sources
