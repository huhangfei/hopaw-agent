# hopaw-agent 运行镜像
FROM eclipse-temurin:17-jre

LABEL maintainer="hopaw-agent"

WORKDIR /app

# 复制 Spring Boot 可执行 jar
COPY hopaw-app-1.0.0.jar app.jar

# 时区
ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

# 运行时数据目录（SQLite 库、附件、项目空间、插件、技能、上传）
# 由 docker-compose 挂载到宿主机持久化，容器重建不丢数据
RUN mkdir -p /app/data
RUN mkdir -p /app/logs

EXPOSE 8080

# 端口级健康检查（应用无 actuator，用 bash 内置 /dev/tcp 探测）
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1

# 容器内使用 prod profile（INFO 日志；dev 配置文件不入库）
# JAVA_OPTS 可在运行时覆盖，例如：docker run -e JAVA_OPTS="-Xmx2g" ...
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Duser.timezone=Asia/Shanghai -jar app.jar --spring.profiles.active=prod"]
