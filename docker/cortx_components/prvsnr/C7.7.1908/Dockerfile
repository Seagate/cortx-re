ARG  CENTOS_RELEASE=77.1908
FROM ssc-vm-0279.colo.seagate.com:5000/centos_eos:${CENTOS_RELEASE}

COPY setup_fpm.sh /tmp/
RUN bash /tmp/setup_fpm.sh \
    && rm -f /tmp/setup_fpm.sh
