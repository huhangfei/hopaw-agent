#!/bin/sh
# hopaw-agent 容器入口脚本
# 1. 启动应用前：若挂载了 Playwright 网页插件，预装其 Chromium（幂等，已安装则仅校验、秒级完成）
#    放在 JVM 启动前执行，独占容器内存，避免安装进程与运行中的应用争抢内存被 OOM Kill（exit 137）
#    浏览器持久化在挂载卷 /root/.cache/ms-playwright（宿主机 ./data/ms-playwright），仅首次真正下载
# 2. 运行期由 docker-compose 注入 PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1，跳过驱动的自动安装（其默认会下载全套浏览器约 1GB）

PLUGIN_JAR=$(ls /app/data/plugins/hopaw-tool-webpage-playwright-*.jar 2>/dev/null | head -n 1)
if [ -n "$PLUGIN_JAR" ]; then
  echo "[entrypoint] 校验/预装 Playwright Chromium: $PLUGIN_JAR"
  if java -cp "$PLUGIN_JAR" com.microsoft.playwright.CLI install chromium; then
    echo "[entrypoint] Playwright Chromium 就绪"
  else
    echo "[entrypoint] 警告: Chromium 预装失败（检查网络/磁盘），网页插件初始化将失败，可重启容器重试"
  fi
else
  echo "[entrypoint] 未发现 Playwright 网页插件，跳过浏览器预装"
fi

# exec 使 java 成为 PID 1，便于接收 SIGTERM 优雅停止
exec java $JAVA_OPTS -Duser.timezone=Asia/Shanghai -cp "/app/lib/*:/app/app.jar" com.agent.hopaw.AgentApplication --spring.profiles.active=prod
