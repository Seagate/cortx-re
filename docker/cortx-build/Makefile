SHELL := /bin/bash
BUILD_ID = 0
RELEASE_VERSION = "2.0.0"
RELEASE_BUILD = "/var/artifacts/${BUILD_ID}"
RPM_DIR = "/var/artifacts/${BUILD_ID}/cortx_iso"
THIRD_PARTY_VERSION = "1.0.0-0"
BRANCH = "main"

.PHONY: help
## help: print this help message.
help : Makefile
	@echo usage: make \"target\"
	@echo
	@echo Please clone required component repositories in cortx-workspace directory before executing respective targets.
	@echo
	@echo targets:
	@sed -n 's/^##/   /p' $<

## validation: validate disk space on cortx-workspace before build
validation:
	@echo "*************** Validate build node *********************"
	if [[ $$(df -BG /cortx-workspace | awk '{ print $$4 }' | tail -n 1 | sed 's/G//') -lt "95" ]]; then echo "cortx-workspace doesn't have sufficient disk space" && exit 1; fi
	
## checkout: checkout specified branch/tag code
checkout:
	@echo "*************** Checkout code *********************"
	pushd cortx-workspace && \
        for component in cortx-motr cortx-hare cortx-rgw-integration cortx-manager cortx-utils cortx-ha; do pushd $$component; git clean -fdx; git checkout .; git pull -p --all; git checkout $$BRANCH || exit 1; popd ; done  && \
		rm -rf cortx-rgw && \
		git clone https://github.com/Seagate/cortx-rgw -b $$BRANCH && \
	popd || (echo "ERROR: Failed to checkout specified branch/tag Exiting..."; exit 1)

## clean: remove existing /var/artifacts/0 directory. 
clean:
	rm -rf "/root/rpmbuild/"
	rm -rf ${RELEASE_BUILD}
	mkdir -p ${RPM_DIR}

## build: generate complete CORTX build including third-party-deps at "/var/artifacts/0"
build: io-path control-path cortx-prereq release_build

## cortx-all-image: generate packages required for cortx-all image including third-party-deps at "/var/artifacts/0"
cortx-all-image: cortx-motr cortx-s3server cortx-hare cortx-prvsnr cortx-manager release_build

## cortx-all-image: generate packages required for cortx-all image including third-party-deps at "/var/artifacts/0"
cortx-all-rockylinux-image: validation cortx-motr cortx-hare cortx-prvsnr cortx-manager cortx-rgw cortx-ha release_build

## control-path: generate control-path packages. cortx-provisioner, cortx-monitor, cortx-manager, cortx-management-portal and cortx-ha.
control-path: cortx-prvsnr cortx-monitor cortx-manager cortx-management-portal cortx-ha

## io-path: generate io-path packages. cortx-motr, cortx-s3server and cortx-hare.
io-path: cortx-motr cortx-s3server cortx-hare

## cortx-motr: generate cortx-motr packages.
cortx-motr: _cortx-motr_prereq _cortx-motr_build

## cortx-s3server: generate cortx-s3server packages.
cortx-s3server: cortx-motr _s3_prereq _s3_build

## cortx-hare: generate cortx-hare packages.
cortx-hare: cortx-motr _cortx-py-utils_build _cortx-py-utils_install _cortx-hare_prereq _cortx-hare_build

## cortx-rgw: generate cortx-rgw packages.
cortx-rgw: validation _cortx-rgw-integration_build cortx-motr _cortx-rgw_prereq _cortx-rgw_build 

## cortx-ha: generate cortx-ha packages.
cortx-ha: _cortx-py-utils_build _cortx-py-utils_install _ha_prereq _ha_build

## cortx-management-portal: generate cortx-management-portal packages.
cortx-management-portal: _cortx-py-utils_build _cortx-py-utils_install cortx-prvsnr _cortx-management-portal_prereq _cortx-management-portal_build

## cortx-manager: generate cortx-manager packages.
cortx-manager: _cortx-py-utils_build _cortx-py-utils_install cortx-prvsnr _cortx-manager_prereq _cortx-manager_build

## cortx-monitor: generate cortx-monitor packages.
cortx-monitor: _cortx-py-utils_build _cortx-py-utils_install _cortx-monitor_prereq _cortx-monitor_build

## cortx-posix: generate cortx-posix (NFS) packages.
cortx-posix: _cortx-posix_prereq _cortx-posix_build

## cortx-prvsnr: generate cortx-prvsnr packages.
cortx-prvsnr: _prvsnr_prereq _prvsnr_build

## cortx-py-utils: genarate cortx-py-utils packages.
cortx-py-utils: _cortx-py-utils_build

#***********************************************************************************
#   Component:  cortx-motr
#	Steps:
#		_cortx-motr_prereq - Install Prerequisites to Generate cortx-motr RPM	
#		_cortx-motr_build  - Script to Generate cortx-motr RPM
#
#***********************************************************************************
_cortx-motr_prereq:
	@echo "***************  Installing cortx-motr Prereqsites *********************"

	pushd cortx-workspace/cortx-motr && \
		export build_number=${BUILD_ID} && \
		cp cortx-motr.spec.in cortx-motr.spec && \
		sed -i "/BuildRequires.*kernel*/d" cortx-motr.spec && \
		sed -i "/BuildRequires.*%{lustre_devel}/d" cortx-motr.spec && \
		sed -i 's/@BUILD_DEPEND_LIBFAB@//g' cortx-motr.spec && \
		sed -i 's/@.*@/111/g' cortx-motr.spec && \
		yum-builddep -y --nogpgcheck cortx-motr.spec && \
		yum -y --nogpgcheck --disablerepo='baseos' --disablerepo='powertools' install libfabric-1.11.2 libfabric-devel-1.11.2 && \
	popd || (echo "ERROR: cortx-motr source code is not available in cortx-workspace. Exiting..."; exit 1)

_cortx-motr_build:
	
	pushd cortx-workspace/cortx-motr && \
		echo "***************  Building cortx-motr RPMS from $$(git rev-parse --short HEAD) *********************" && \
		./autogen.sh || \
		./autogen.sh && \
		./configure --with-user-mode-only && \
		export build_number=${BUILD_ID} && \
		make rpms && \
		rm -rf ${RPM_DIR}/cortx-motr*.rpm && \
		mv /root/rpmbuild/RPMS/x86_64/*.rpm ${RPM_DIR} && \
		createrepo -v ${RPM_DIR} && \
	popd
	

#***********************************************************************************
#   Component:  S3
#	Steps:
#		_s3_prereq - Install Prerequisites to Generate S3 RPM	
#		_s3_build  - Script to Generate S3 RPM
#
#***********************************************************************************
_s3_prereq:
	@echo "***************  Installing S3 Prereqsites *********************"

	pushd cortx-workspace/cortx-s3server && \
	  yum localinstall -y ${RPM_DIR}/cortx-motr-[0-9]*.rpm ${RPM_DIR}/cortx-motr-devel-[0-9]*.rpm && \
		yum clean all;rm -rf /var/cache/yum && \
	popd || (echo "ERROR: cortx-s3server source code is not available in cortx-workspace. Exiting..."; exit 1)

_s3_build:
	@echo "***************  Building S3 RPMS *********************"
	
	pushd cortx-workspace/cortx-s3server && \
		yum clean all;rm -rf /var/cache/yum && \
		export build_number=${BUILD_ID} && \
		./rpms/s3/buildrpm.sh -S ${RELEASE_VERSION} -G $$(git rev-parse --short HEAD) && \
		./rpms/s3iamcli/buildrpm.sh -S ${RELEASE_VERSION} -G $$(git rev-parse --short HEAD) && \
		./rpms/s3test/buildrpm.sh -S ${RELEASE_VERSION} -G $$(git rev-parse --short HEAD) && \
		rm -rf {RPM_DIR}/cortx-s3*.rpm && \
		mv /root/rpmbuild/RPMS/x86_64/*.rpm ${RPM_DIR} && \
       	mv /root/rpmbuild/RPMS/noarch/*.rpm ${RPM_DIR} && \
		createrepo -v ${RPM_DIR} && \
	popd

#***********************************************************************************
#   Component:  cortx-hare
#	Steps:
#		_cortx-hare_prereq - Install Prerequisites to Generate cortx-hare RPM	
#		_cortx-hare_build  - Script to Generate cortx-hare RPM
#
#***********************************************************************************
_cortx-hare_prereq:
	@echo "***************  Installing cortx-hare Prereqsites *********************"

	pushd cortx-workspace/cortx-hare && \
		yum localinstall -y ${RPM_DIR}/cortx-py-utils*noarch.rpm ${RPM_DIR}/cortx-motr-[0-9]*.rpm ${RPM_DIR}/cortx-motr-devel-[0-9]*.rpm && \
	popd || (echo "ERROR: cortx-hare source code is not available in cortx-workspace. Exiting..."; exit 1)

_cortx-hare_build:
	@echo "***************  Building cortx-hare RPMS *********************"
	
	pushd cortx-workspace/cortx-hare && \
		echo "Executing build script" && \
		export build_number=${BUILD_ID} && \
		make VERSION=${RELEASE_VERSION} rpm && \
		rm -rf ${RPM_DIR}/cortx-hare*.rpm && \
		mv /root/rpmbuild/RPMS/x86_64/*.rpm ${RPM_DIR} && \
		createrepo -v ${RPM_DIR} && \
	popd


#***********************************************************************************
#   Component:  cortx-rgw
#	Steps:
#		_cortx-rgw_prereq - Install Prerequisites to Generate cortx-rgw RPM	
#		_cortx-rgw_build  - Script to Generate cortx-rgw RPM
#
#***********************************************************************************
_cortx-rgw_prereq:
	@echo "***************  Installing cortx-rgw Prereqsites *********************"

	pushd cortx-workspace/cortx-rgw && \
		./install-deps.sh && \
		./make-dist && \
		mkdir -p /root/rpmbuild/RPMS/x86_64/{BUILD,BUILDROOT,RPMS,SOURCES,SPECS,SRPMS} && \
		mv ceph*.tar.bz2 /root/rpmbuild/RPMS/x86_64/SOURCES/ && \
		tar --strip-components=1 -C /root/rpmbuild/RPMS/x86_64/SPECS/ --no-anchored -xvjf /root/rpmbuild/RPMS/x86_64/SOURCES/ceph*.tar.bz2 "ceph.spec" && \
		yum localinstall -y ${RPM_DIR}/cortx-motr-[0-9]*.rpm ${RPM_DIR}/cortx-motr-devel-[0-9]*.rpm && \
	popd || (echo "ERROR: cortx-rgw source code is not available in cortx-workspace. Exiting..."; exit 1)

_cortx-rgw_build:
	@echo "***************  Building cortx-rgw RPMS *********************"
	
	pushd /root/rpmbuild/RPMS/x86_64 && \
		echo "Executing build script" && \
		export build_number=${BUILD_ID} && \
		rpmbuild --clean --rmsource --define "_unpackaged_files_terminate_build 0" --define "debug_package %{nil}" --without cmake_verbose_logging --without jaeger --without lttng --without seastar --without kafka_endpoint --without zbd --without cephfs_java --without cephfs_shell --without ocf --without selinux --without ceph_test_package --without make_check --define "_binary_payload w2T16.xzdio" --define "_topdir `pwd`" -ba ./SPECS/ceph.spec && \
		mv /root/rpmbuild/RPMS/x86_64/RPMS/*/*.rpm ${RPM_DIR} && \
		createrepo -v ${RPM_DIR} && \
	popd

#***********************************************************************************
#   Component:  cortx-rgw-integration
#       Steps:
#               _cortx-rgw-integration_prereq - Install Prerequisites to Generate cortx-rgw-integration RPM
#               _cortx-rgw-integration_build  - Script to Generate cortx-rgw-integration RPM
#
#***********************************************************************************

_cortx-rgw-integration_build:
	@echo "***************  Building cortx-rgw-integration RPMS *********************"
		pushd cortx-workspace/cortx-rgw-integration/ && \
			rm -rf dist/cortx-rgw-integration*.rpm && \
			if [ -f ./jenkins/build.sh ]; then bash ./jenkins/build.sh -v ${RELEASE_VERSION} -b ${BUILD_ID} && mv ./dist/cortx-rgw-integration*.rpm ${RPM_DIR}; fi && \
			createrepo -v ${RPM_DIR} && \
		popd

#***********************************************************************************
#   Component:  cortx-posix
#	Steps:
#		_cortx-posix_prereq - Install Prerequisites to Generate cortx-posix RPM	
#		_cortx-posix_build  - Script to Generate cortx-posix RPM
#
#***********************************************************************************
_cortx-posix_prereq:
	@echo "***************  Installing cortx-posix Prereqsites *********************"

	pushd cortx-workspace/cortx-posix && \
		yum localinstall -y ${RPM_DIR}/cortx-motr-[0-9]*.rpm ${RPM_DIR}/cortx-motr-devel-[0-9]*.rpm && \
		yum install libevhtp libevhtp-devel libevent libevent-devel userspace-rcu-devel dbus-devel -y && \
		sed -i 's/QA_CHECK_RPATHS=1/QA_CHECK_RPATHS=0/g' ~/.rpmmacros && \
	popd || (echo "ERROR: cortx-posix source code is not available in cortx-workspace. Exiting..."; exit 1)

_cortx-posix_build:
	@echo "***************  Building cortx-posix RPMS *********************"
	git clone https://github.com/Seagate/nfs-ganesha --recursive --branch 2.8-stable-cortx
	pushd cortx-workspace/cortx-posix && \
			VERSION=$$(cat VERSION) && \
			echo "Executing build script" && \
			echo "VERSION:$$VERSION" && \
			./jenkins/build.sh -v $$VERSION -b ${BUILD_ID} -p ../../nfs-ganesha/src/  && \
			cp /root/rpmbuild/RPMS/x86_64/*.rpm ${RPM_DIR} && \
			createrepo -v ${RPM_DIR} && \
	popd
	

#***********************************************************************************
#   Component:  cortx-py-utils
#       Steps:
#               _cortx-py-utils_build  - Script to Generate cortx-py-utils RPM
#
#***********************************************************************************

_cortx-py-utils_build:
	@echo "***************  Building cortx-py-utils package *********************"
	pushd cortx-workspace/cortx-utils/ && \
		yum install python36-devel -y && \
		./jenkins/build.sh -v ${RELEASE_VERSION} -b ${BUILD_ID} && \
		./statsd-utils/jenkins/build.sh -b ${BUILD_ID} && \
		rm -rf ${RPM_DIR}/cortx-py-utils*.rpm && \
		mv /cortx-workspace/cortx-utils/statsd-utils/dist/rpmbuild/RPMS/x86_64/*.rpm ${RPM_DIR} && \
		mv /cortx-workspace/cortx-utils/py-utils/dist/*.rpm ${RPM_DIR} && \
		createrepo -v ${RPM_DIR} && \
	popd || (echo "ERROR: cortx-utils source code is not available in cortx-workspace. Exiting..."; exit 1)

_cortx-py-utils_install:
	@echo "***************  Installing cortx-py-utils package *********************"
	pushd cortx-workspace/cortx-utils/py-utils && \
		yum install python3-pip gcc python3-devel -y && \
		if [[ $$(cat /etc/redhat-release) =~ "Rocky" ]]; then yum erase python3-PyYAML -y ;else yum erase python36-PyYAML -y ; fi && \
		pip3 install -r python_requirements.txt && \
		pip3 install -r python_requirements.ext.txt && \
		yum localinstall -y ${RPM_DIR}/cortx-py-utils*noarch.rpm && \
	popd || (echo "ERROR: Failed to install cortx-utils package. Exiting..."; exit 1) 	

#***********************************************************************************
#   Component:  cortx-management-portal
#	Steps:
#		_cortx-management-portal_prereq - Install Prerequisites to Generate cortx-management-portal RPM	
#		_cortx-management-portal_build  - Script to Generate cortx-management-portal RPM
#
#***********************************************************************************
_cortx-management-portal_prereq:
	@echo "***************  Installing cortx-management-portal Prereqsites *********************"
	pushd cortx-workspace/cortx-management-portal && \
		if [[ $$(cat /etc/redhat-release) =~ "Rocky" ]]; then yum localinstall -y ${RPM_DIR}/cortx-provisioner*.noarch.rpm ;else yum localinstall ${RPM_DIR}/cortx-prvsnr*.rpm ${RPM_DIR}/python36-cortx-prvsnr*.rpm ; fi && \
        pip3.6 install pyinstaller==3.5 && \
	popd || (echo "ERROR: cortx-management-portal source code is not available in cortx-workspace. Exiting..."; exit 1)

_cortx-management-portal_build:
	@echo "***************  Building cortx-management-portal RPMS *********************"
	
	pushd cortx-workspace/cortx-management-portal && \
		BUILD=$$(git rev-parse --short HEAD) && \
		echo "Python:$$(python --version)" && \
		./cicd/build.sh -v ${RELEASE_VERSION} -v ${RELEASE_VERSION} -b "${BUILD_ID}" -t -i && \
		rm -rf ${RPM_DIR}/cortx-csm_web*.rpm && \
		mv ./dist/rpmbuild/RPMS/x86_64/*.rpm ${RPM_DIR} && \
		createrepo -v ${RPM_DIR} && \
	popd


#***********************************************************************************
#   Component:  cortx-manager
#	Steps:
#		_cortx-manager_prereq - Install Prerequisites to Generate cortx-manager RPM	
#		_cortx-manager_build  - Script to Generate cortx-manager RPM
#
#***********************************************************************************
_cortx-manager_prereq:
	@echo "***************  Installing cortx-manager Prereqsites *********************"

	pushd cortx-workspace/cortx-manager && \
		if [[ $$(cat /etc/redhat-release) =~ "Rocky" ]]; then yum localinstall -y ${RPM_DIR}/cortx-provisioner*.noarch.rpm ;else yum localinstall -y ${RPM_DIR}/cortx-prvsnr*.rpm ${RPM_DIR}/python36-cortx-prvsnr*.rpm ; fi && \
        pip3.6 install pyinstaller==3.5 && \
	popd || (echo "ERROR: cortx-manager source code is not available in cortx-workspace. Exiting..."; exit 1)

_cortx-manager_build:
	@echo "***************  Building cortx-manager RPMS *********************"
	
	pushd cortx-workspace/cortx-manager && \
		BUILD=$$(git rev-parse --short HEAD) && \
		echo "Python:$(python --version)" && \
		./cicd/build.sh -v ${RELEASE_VERSION} -b "${BUILD_ID}" -t -i || \
		rm -rf ${RPM_DIR}/cortx-csm_agent*.rpm && \
		mv ./dist/rpmbuild/RPMS/x86_64/*.rpm ${RPM_DIR} && \
		createrepo -v ${RPM_DIR} && \
	popd

#***********************************************************************************
#   Component:  cortx-prvsnr
#	Steps:
#		_prvsnr_prereq - Install Prerequisites to Generate cortx-prvsnr RPM	
#		_prvsnr_build  - Script to Generate cortx-prvsnr RPM
#
#***********************************************************************************
_prvsnr_prereq:
	@echo "***************  Installing cortx-prvsnr Prereqsites *********************"

	pushd cortx-workspace/cortx-prvsnr/ && \
		if [[ $$(cat /etc/redhat-release) =~ "Rocky" ]]; then yum install -y python3 ;else yum install -y python ; fi && \
	popd || (echo "ERROR: cortx-prvsnr codebase is not available in cortx-workspace.Exiting..."; exit 1)

_prvsnr_build:
	@echo "***************  Building cortx-prvsnr RPMS *********************"
	
	pushd cortx-workspace/cortx-prvsnr/ && \
		rm -rf dist/*.rpm ${RPM_DIR}/cortx-pr*.rpm ${RPM_DIR}/python36-cortx-prvsnr*.rpm ${RPM_DIR}/python36-cortx-setup*.rpm && \
		if [[ $$(cat /etc/redhat-release) =~ "Rocky" ]]; then \
			if [ -f ./jenkins/build.sh ]; then bash ./jenkins/build.sh -v ${RELEASE_VERSION} -b ${BUILD_ID} && mv ./dist/cortx-provisioner*.rpm ${RPM_DIR} ; fi ; else sh ./devops/rpms/buildrpm.sh -g "$$(git rev-parse --short HEAD)" -e ${RELEASE_VERSION} -b "${BUILD_ID}" && \
			sh ./cli/buildrpm.sh -g "$$(git rev-parse --short HEAD)" -e ${RELEASE_VERSION} -b "${BUILD_ID}" && \
			bash ./devops/rpms/api/build_python_api.sh -vv --out-dir /root/rpmbuild/RPMS/x86_64/ --pkg-ver ${BUILD_ID}_git$$(git rev-parse --short HEAD) && \
			if [ -f ./devops/rpms/lr-cli/build_python_cortx_setup.sh ]; then bash ./devops/rpms/lr-cli/build_python_cortx_setup.sh -vv --out-dir /root/rpmbuild/RPMS/x86_64/ --pkg-ver ${BUILD_ID}_git$$(git rev-parse --short HEAD); fi && \
			sh ./devops/rpms/node_cli/node_cli_buildrpm.sh -g "$$(git rev-parse --short HEAD)" -e ${RELEASE_VERSION} -b ${BUILD_ID} && \
			if [ -f ./jenkins/build.sh ]; then bash ./jenkins/build.sh -v ${RELEASE_VERSION} -b ${BUILD_ID} && mv ./dist/cortx-provisioner-*noarch.rpm ${RPM_DIR}; fi && \
			mv /root/rpmbuild/RPMS/x86_64/*.rpm ${RPM_DIR} ; fi && \
		createrepo -v ${RPM_DIR} && \
	popd

#***********************************************************************************
#   Component:  sspl
#	Steps:
#		_sspl_prereq - Install Prerequisites to Generate sspl RPM	
#		_sspl_build  - Script to Generate sspl RPM
#
#***********************************************************************************
_cortx-monitor_prereq:
	@echo "***************  Installing sspl Prereqsites *********************"

	pushd cortx-workspace/cortx-monitor && \
		yum localinstall -y ${RPM_DIR}/cortx-py-utils*noarch.rpm && \
		yum install sudo python-Levenshtein libtool doxygen python-pep8 openssl-devel graphviz check-devel -y && \
	popd || (echo "ERROR: cortx-monitor source code is not available in cortx-workspace. Exiting..."; exit 1)

_cortx-monitor_build:
	@echo "***************  Building sspl RPMS *********************"
	
	pushd cortx-workspace/cortx-monitor && \
		export build_number=${BUILD_ID} && \
		echo "Executing build script" && \
		./jenkins/build.sh -v ${RELEASE_VERSION} -l DEBUG && \
		rm -rf ${RPM_DIR}/*sspl*.rpm && \
		mv /root/rpmbuild/RPMS/x86_64/*.rpm ${RPM_DIR} && \
        mv /root/rpmbuild/RPMS/noarch/*.rpm ${RPM_DIR} && \
		createrepo -v ${RPM_DIR} && \
	popd

#***********************************************************************************
#   Component:  HA
#	Steps:
#		_ha_prereq - Install Prerequisites to Generate HA RPM	
#		_ha_build  - Script to Generate HA RPM
#
#***********************************************************************************
_ha_prereq:
	@echo "***************  Installing HA Prereqsites *********************"
	
	pushd "cortx-workspace/cortx-ha" && \
		yum clean all;rm -rf /var/cache/yum && \
		yum erase python36-PyYAML -y && \
		bash ./jenkins/cicd/cortx-ha-dep.sh  && \
		pip3 install numpy && \
	popd || (echo "ERROR: cortx-ha source code is not available in cortx-workspace. Exiting..."; exit 1)

_ha_build:
	@echo "***************  Building HA RPMS *********************"
	
	pushd "cortx-workspace/cortx-ha" && \
		./jenkins/build.sh -v ${RELEASE_VERSION} -b ${BUILD_ID} && \
		rm -rf ${RPM_DIR}/cortx-ha-*.rpm && \
		mv dist/rpmbuild/RPMS/*/*.rpm ${RPM_DIR} && \
		createrepo -v ${RPM_DIR} && \
	popd

#***********************************************************************************
#   Component:  DevOps
#	Steps:
#		release_build - Setup yum repo, Generate RELEASE.INFO, gpg key sigining.
#
#***********************************************************************************
cortx-prereq:
	@echo "***************  Generating cortx-prereq RPM *********************"

	pushd /opt/third-party-rpm/ && \
		./build-prerequisite-rpm.sh -v ${RELEASE_VERSION} -r ${BUILD_ID} -g $$GIT_HASH && \
		rm -rf ${RPM_DIR}/cortx-prereq*.rpm && \
		mv /root/rpmbuild/RPMS/x86_64/*.rpm ${RPM_DIR} && \
	popd

release_build:
	@echo "***************  Generating Release Build *********************"
	pushd ${RPM_DIR} && \
		createrepo -v . && \
	popd && \
	pushd ${RELEASE_BUILD} && \
		if [[ $$(cat /etc/redhat-release) =~ "Rocky" ]]; then wget -q https://github.com/Seagate/cortx/releases/download/third-party-deps-${THIRD_PARTY_VERSION}/third-party-rockylinux-8.4-${RELEASE_VERSION}-${BUILD_ID}.tar.gz -O third-party-${THIRD_PARTY_VERSION}.tar.gz ; else wget -q https://github.com/Seagate/cortx/releases/download/third-party-deps-${THIRD_PARTY_VERSION}/third-party-$$(rpm -qa centos-release | sed 's/-/./g' | awk -F[.] '{ print $$1"-"$$3"."$$4"."$$5}')-${THIRD_PARTY_VERSION}.tar.gz -O third-party-${THIRD_PARTY_VERSION}.tar.gz ; fi && \
		wget -q https://github.com/Seagate/cortx/releases/download/third-party-deps-${THIRD_PARTY_VERSION}/python-deps-${THIRD_PARTY_VERSION}.tar.gz && \
		mkdir -p 3rd_party && tar -xvzf third-party-${THIRD_PARTY_VERSION}.tar.gz -C 3rd_party --strip-components=1 && \
		mkdir -p python_deps && tar -xvzf python-deps-${THIRD_PARTY_VERSION}.tar.gz -C python_deps --strip-components=1 && \
		if [[ $$(cat /etc/redhat-release) =~ "CentOS" ]]; then wget -O install-${RELEASE_VERSION}-${BUILD_ID}.sh https://raw.githubusercontent.com/Seagate/cortx-prvsnr/$$(find /var/artifacts/*/cortx_iso/ -name cortx-provisioner*noarch.rpm| rev|awk -F'.' '{print $$3}'|awk -F'_' '{print $$1}'|rev)/srv/components/provisioner/scripts/install.sh ; fi && \
 		sh /opt/release_support/build_release_info.sh -b ${BRANCH} -v ${RELEASE_VERSION} -l ${RPM_DIR} -t ${RELEASE_BUILD}/3rd_party && \
		cp ${RPM_DIR}/RELEASE.INFO ${RELEASE_BUILD}/ && \
	popd

## iso-generation: generate ISO file from release build.
iso-generation:
	@echo "***************  Generating ISO file *********************"
	
	pushd ${RELEASE_BUILD} && \
		mkdir -p ${RELEASE_BUILD}/iso && \
		genisoimage -input-charset iso8859-1 -exclude "python-deps-${THIRD_PARTY_VERSION}.tar.gz" -m "third-party-centos-7.8.2003-${THIRD_PARTY_VERSION}.tar.gz" -f -J -joliet-long -r -allow-lowercase -allow-multidot -publisher Seagate -o ${RELEASE_BUILD}/iso/cortx-1.0.0-${BUILD_ID}-single.iso ${RELEASE_BUILD} && \
	popd
