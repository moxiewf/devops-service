FROM registry.cn-shanghai.aliyuncs.com/c7n/devops-base:0.7.5 as base
FROM registry.cn-shanghai.aliyuncs.com/c7n/javabase:0.8.2
# Install kaniko
COPY --from=base /usr/lib/yaml /usr/lib/yaml
COPY --from=base /usr/bin/kaniko /usr/bin/kaniko
COPY app.jar /devops-service.jar
CMD java $JAVA_OPTS $SKYWALKING_OPTS -jar /devops-service.jar