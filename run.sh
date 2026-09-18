#!/bin/bash
# Hopaw Agent 启停脚本（Linux 版）
# 用法：
#   ./run.sh          启动（后台运行，等待端口就绪后打开浏览器）
#   ./run.sh stop     停止（按端口杀进程）

cd "$(dirname "$0")" || exit 1

PORT=8080
PID_FILE="$(pwd)/hopaw-agent.pid"
TIMEOUT=30

# 分包启动目录（与 Docker 镜像内布局一致）：app.jar + libs
APP_JAR="hopaw-app/target/hopaw-app-1.0.0.jar"
LIBS_DIR="hopaw-app/target/docker-libs"
# 可执行 fat jar（classifier=exec）
EXEC_JAR="hopaw-app/target/hopaw-app-1.0.0-exec.jar"

# 1. 处理停止命令
if [ "$1" == "stop" ]; then
    echo "Killing process on port ${PORT}..."
    PIDS=$(ss -tlnp 2>/dev/null | grep ":${PORT} " | grep -oP 'pid=\K[0-9]+' | sort -u)
    if [ -z "$PIDS" ]; then
        # 兜底：ss 不可用时尝试 lsof
        PIDS=$(lsof -t -i:"${PORT}" 2>/dev/null | sort -u)
    fi
    if [ -z "$PIDS" ]; then
        echo "No process listening on port ${PORT}."
        exit 0
    fi
    for PID in $PIDS; do
        echo "Found PID: ${PID}, terminating..."
        kill -9 "$PID"
    done
    rm -f "$PID_FILE"
    echo "Done."
    exit 0
fi

echo "============================================"
echo "  Hopaw Agent Starting..."
echo "============================================"

# 2. 检查 Java 环境
if ! command -v java > /dev/null 2>&1; then
    echo "[ERROR] Java not found in PATH."
    exit 1
fi

# 3. 选择启动方式：优先 exec fat jar；仅有分包产物时以 -cp 分包启动（与容器一致）
LAUNCH_MODE=""
if [ -f "$EXEC_JAR" ]; then
    LAUNCH_MODE="exec"
elif [ -f "$APP_JAR" ] && [ -d "$LIBS_DIR" ] && ls "$LIBS_DIR"/*.jar > /dev/null 2>&1; then
    LAUNCH_MODE="split"
else
    echo "[ERROR] No runnable build found."
    echo "Please run: mvn clean package -DskipTests (and maven-package.sh for split mode)"
    exit 1
fi

# 4. 启动进程（后台，日志重定向）
echo "Starting background process (mode: ${LAUNCH_MODE})..."
if [ "$LAUNCH_MODE" == "exec" ]; then
    nohup java -jar "$EXEC_JAR" > hopaw-agent.out 2>&1 &
else
    nohup java -cp "${LIBS_DIR}/*:${APP_JAR}" com.agent.hopaw.AgentApplication > hopaw-agent.out 2>&1 &
fi
echo $! > "$PID_FILE"

# 5. 等待端口监听（带超时）
echo "Waiting for port ${PORT} ..."
count=0
while true; do
    sleep 2
    count=$((count + 1))
    if [ "$count" -ge "$TIMEOUT" ]; then
        echo "[ERROR] Startup timed out. Check logs for details."
        exit 1
    fi
    if curl -s -o /dev/null -m 2 "http://localhost:${PORT}"; then
        break
    fi
done

# 6. 打开浏览器（有桌面环境时）
echo "Opening browser..."
if command -v xdg-open > /dev/null 2>&1; then
    xdg-open "http://localhost:${PORT}" > /dev/null 2>&1
fi

echo "============================================"
echo "  Ready. PID: $(cat "$PID_FILE"), log: hopaw-agent.out"
echo "  Use './run.sh stop' kill the ${PORT}"
echo "============================================"
