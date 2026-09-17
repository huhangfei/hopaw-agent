# hopaw-agent 运行镜像（分包结构）
# 前置条件：先在宿主机构建（由 docker-build.bat 完成）
#   mvn clean package -DskipTests
#   mvn dependency:copy-dependencies ...（产物收集到 hopaw-app/target/docker-libs）
# 布局：
#   /app/app.jar          应用自身类（hopaw-app 模块，普通 jar）
#   /app/lib/*.jar        全部依赖（含 hopaw-contract/infra/biz/avatar 模块 jar + 第三方依赖）
# 更新单个模块（如只改了 hopaw-infra）：
#   重新构建该模块 -> 替换 hopaw-infra-1.0.0.jar -> 重打镜像，重启容器
FROM eclipse-temurin:17-jre

LABEL maintainer="hopaw-agent"

WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends fonts-wqy-zenhei && \
    rm -rf /var/lib/apt/lists/* \

RUN apt-get update && apt-get install -y --no-install-recommends \
    fonts-wqy-zenhei \
    libglib2.0-0 libnss3 libnspr4 libdbus-1-3 libatk1.0-0 libatk-bridge2.0-0 \
    libcups2 libdrm2 libxcb1 libxkbcommon0 libatspi2.0-0 libx11-6 \
    libxcomposite1 libxdamage1 libxext6 libxfixes3 libxrandr2 libgbm1 \
    libpango-1.0-0 libcairo2 libasound2 libxshmfence1 \
    && rm -rf /var/lib/apt/lists/*

# Playwright: 跳过运行时浏览器下载（Docker构建时预装）
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

# 应用自身类 jar
COPY hopaw-app/target/hopaw-app-1.0.0.jar app.jar

# 依赖库（模块 jar + 第三方 jar）
COPY hopaw-app/target/docker-libs/ lib/

ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

# 运行时数据目录（SQLite 库、附件、项目空间、插件、技能、上传）
# 由 docker-compose 挂载到宿主机持久化，容器重建不丢数据
RUN mkdir -p /app/data

EXPOSE 8080

# 端口级健康检查（应用无 actuator，用 bash 内置 /dev/tcp 探测）
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1

# 分包启动：classpath 指向 app.jar + lib 下全部 jar
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Duser.timezone=Asia/Shanghai -cp \"/app/lib/*:/app/app.jar\" com.agent.hopaw.AgentApplication --spring.profiles.active=prod"]
