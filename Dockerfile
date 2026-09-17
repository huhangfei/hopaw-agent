# hopaw-agent 运行镜像（分包结构）
# 前置条件：先在宿主机构建（由 docker-build.bat 完成�?#   mvn clean package -DskipTests
#   mvn dependency:copy-dependencies ...（产物收集到 hopaw-app/target/docker-libs�?# 布局�?#   /app/app.jar          应用自身类（hopaw-app 模块，普�?jar�?#   /app/lib/*.jar        全部依赖（含 hopaw-contract/infra/biz/avatar 模块 jar + 第三方依赖）
# 更新单个模块（如只改�?hopaw-infra）：
#   重新构建该模�?-> 替换 hopaw-infra-1.0.0.jar -> 重打镜像，重启容�?FROM eclipse-temurin:17-jre

LABEL maintainer="hopaw-agent"

WORKDIR /app

# 中文字体 + Chromium 运行时依赖（Playwright 网页插件�?RUN apt-get update && apt-get install -y --no-install-recommends \
    fonts-wqy-zenhei \
    libglib2.0-0 libnss3 libnspr4 libdbus-1-3 libatk1.0-0 libatk-bridge2.0-0 \
    libcups2 libdrm2 libxcb1 libxkbcommon0 libatspi2.0-0 libx11-6 \
    libxcomposite1 libxdamage1 libxext6 libxfixes3 libxrandr2 libgbm1 \
    libpango-1.0-0 libcairo2 libasound2t64 libxshmfence1 \
    && rm -rf /var/lib/apt/lists/*

# Playwright: 跳过运行期浏览器自动下载（其默认会下载全套浏览器�?1GB，且与运行中的应用争抢内存导�?OOM Kill exit 137�?# 浏览器由入口脚本 docker-entrypoint.sh 在应用启动前用插件自带的驱动预装（版本精确匹配），并持久化在挂载卷中
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

# 应用自身�?jar
COPY hopaw-app/target/hopaw-app-1.0.0.jar app.jar

# 依赖库（模块 jar + 第三�?jar�?COPY hopaw-app/target/docker-libs/ lib/

# 入口脚本（应用启动前预装 Playwright Chromium + 启动应用�?# sed 兜底清除 Windows 检出可能引入的 CRLF 行尾
COPY docker-entrypoint.sh /entrypoint.sh
RUN sed -i 's/\r$//' /entrypoint.sh && chmod +x /entrypoint.sh

ENV TZ=Asia/Shanghai \
    JAVA_OPTS=""

# 运行时数据目录（SQLite 库、附件、项目空间、插件、技能、上传）
# �?docker-compose 挂载到宿主机持久化，容器重建不丢数据
RUN mkdir -p /app/data

EXPOSE 8080

# 端口级健康检查（应用�?actuator，用 bash 内置 /dev/tcp 探测�?HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1

# 分包启动：classpath 指向 app.jar + lib 下全�?jar（预装逻辑�?docker-entrypoint.sh�?ENTRYPOINT ["/entrypoint.sh"]
