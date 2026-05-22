# Dockerfile for hopaw-plugin-repo
# 使用已构建的 JAR 文件

FROM eclipse-temurin:17-jre-alpine

# 设置标签信息
LABEL maintainer="hopaw-agent"
LABEL description="Hopaw Plugin Repository Service"
LABEL version="1.0.0"

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 创建应用用户（安全最佳实践）
RUN addgroup -S hopaw && adduser -S hopaw -G hopaw

# 设置工作目录
WORKDIR /app

# 创建必要的目录
RUN mkdir -p /app/logs /app/plugin-packages /app/data

# 复制已构建的 JAR 文件
COPY hopaw-plugin-repo-1.0.0.jar app.jar

# 设置目录权限
RUN chown -R hopaw:hopaw /app

# 切换到非 root 用户
USER hopaw

# 暴露端口
EXPOSE 8081

# JVM 参数配置
ENV JAVA_OPTS="-Xms256m -Xmx256m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom \
    -Duser.timezone=Asia/Shanghai"

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=40s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/ || exit 1

# 启动应用
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
