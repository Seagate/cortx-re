FROM docker.pkg.github.com/shailesh-vaidya/cortx-re/eos_centos:7.7.1908

#Remove cortx specific repositories. 
#RUN rm -rf /etc/yum.repos.d/CentOS-*.repo
#COPY *.repo  /etc/yum.repos.d/

# upgrade all
RUN yum --assumeyes upgrade --exclude=centos-release && \
    yum clean all \
    && rm -rf /var/cache/yum

# install node 
ADD install-node.sh /opt/install-node.sh
RUN sh /opt/install-node.sh v12.13.0

# docker-systemctl-replacement
ADD systemctl-replacement.sh /opt/systemctl-replacement.sh
RUN sh /opt/systemctl-replacement.sh

RUN yum clean all \
    && rm -rf /var/cache/yum
