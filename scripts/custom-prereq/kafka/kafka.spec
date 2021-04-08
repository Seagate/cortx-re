%undefine _disable_source_fetch

Name:           kafka
Version:        %{version}
Release:        1%{?dist}
Summary:        Apache Kafka is publish-subscribe messaging rethought as a distributed commit log.

Group:          Applications
License:        Apache License, Version 2.0
URL:            https://kafka.apache.org/
Source0:        https://mirrors.estointernet.in/apache/kafka/2.7.0/kafka_2.13-2.7.0.tgz


%description
Kafka is designed to allow a single cluster to serve as the central data backbone for a large organization. It can be elastically and transparently expanded without downtime. Data streams are partitioned and spread over a cluster of machines to allow data streams larger than the capability of any single machine and to allow clusters of co-ordinated consumers. Messages are persisted on disk and replicated within the cluster to prevent data loss.

%global debug_package %{nil}

%prep
%setup -n kafka_2.13-2.7.0.tgz

%build
rm -f libs/{kafka_*-javadoc.jar,kafka_*-scaladoc.jar,kafka_*-sources.jar,*.asc}
rm config/zookeeper.properties

%install
mkdir -p $RPM_BUILD_ROOT%{_prefix}/kafka
mkdir $RPM_BUILD_ROOT%{_prefix}/kafka/bin
cp bin/kafka-*.sh $RPM_BUILD_ROOT%{_prefix}/kafka/bin/
cp -r libs $RPM_BUILD_ROOT%{_prefix}/kafka/
cp -r config $RPM_BUILD_ROOT%{_prefix}/kafka/
mkdir -p $RPM_BUILD_ROOT/etc/rc.d/init.d
install -m 755 %{S:1} $RPM_BUILD_ROOT/etc/rc.d/init.d/kafka
mkdir -p $RPM_BUILD_ROOT/var/log/kafka

%clean
rm -rf $RPM_BUILD_ROOT

%files
%defattr(-,root,root)
%attr(0755,kafka,kafka) %dir /opt/kafka
%attr(0755,kafka,kafka) /opt/kafka/bin
%attr(0755,kafka,kafka) /opt/kafka/libs
%config(noreplace) %attr(755,kafka,kafka) /opt/kafka/config
%attr(0775,root,kafka) /etc/rc.d/init.d/kafka
%attr(0755,kafka,kafka) %dir /var/log/kafka

%changelog