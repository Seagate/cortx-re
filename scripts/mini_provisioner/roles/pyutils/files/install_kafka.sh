yum install java java-devel -y
cd /opt
curl "http://cortx-storage.colo.seagate.com/releases/cortx/third-party-deps/centos/centos-7.8.2003-2.0.0-latest/commons/kafka/kafka_2.13-2.7.0.tgz" -o kafka.tgz
tar -xzf kafka.tgz
mv kafka_2.13-2.7.0 kafka
cd kafka
bin/zookeeper-server-start.sh -daemon config/zookeeper.properties
bin/kafka-server-start.sh -daemon config/server.properties