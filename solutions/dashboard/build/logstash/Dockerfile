FROM logstash:8.4.1

SHELL ["/bin/bash", "-c"]
USER root

RUN apt-get update \
    && apt-get install -y --no-install-recommends unzip=6.0-25ubuntu1.1 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN curl -LO https://dbschema.com/jdbc-drivers/MongoDbJdbcDriver.zip \
    && unzip MongoDbJdbcDriver.zip -d /usr/share/logstash/logstash-core/lib/jars \
    && echo -e "\n\n----------------------------\nMongoDB Driver Version:" $(cut -c 10-12 <<< $(ls /usr/share/logstash/logstash-core/lib/jars | grep mongojdbc)) "\n----------------------------\n\n"

USER logstash
SHELL ["/bin/sh", "-c"]