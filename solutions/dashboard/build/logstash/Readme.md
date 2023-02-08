### Logstash Dockerfile

A Dockerfile to build custom image from logstash base image.

In logstash pipeline MongoDbJdbcDriver used to move the documents from MongoDB to Elasticsearch. So, the driver .jar files should be there. In dockerfile commands written to fetch the logstash .jar files, extract them and place them in specific location to used by pipeline.

We need the version *4.8* of MongoDbJdbc. While building the image we are also displaying driver version, to track the version easily and if any change then making the changes accordingly.

Command to build image:

Run below command by replacing \<image_name> and \<tag>

```
docker build -t <image_name>:<tag> . --no-cache --progress plain
```