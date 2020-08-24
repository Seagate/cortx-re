FROM ssc-vm-0279.colo.seagate.com:5000/rhel_cortx:7.7.1908

COPY setup_fpm.sh /tmp/
RUN bash /tmp/setup_fpm.sh \
    && rm -f /tmp/setup_fpm.sh
