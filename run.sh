#!/bin/bash
# Hopaw Agent 启停脚本（Linux 版）
# 用法：
#   ./run.sh          启动（后台运行，等待端口就绪后打开浏览器）
#   ./run.sh stop     停止（按端口杀进程）

cd "$(dirname "$0")" || exit 1

JAR_FILE="hopaw-app/target/hopaw-app-1.0.0.jar"
PORT=8080
PID_FILE="$(pwd)/hopaw-agent.pid"
TIMEOUT=30

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

# 3. 检查 JAR 文件
if [ ! -f "$JAR_FILE" ]; then
    echo "[ERROR] ${JAR_FILE} not found"
    echo "Please run: mvn package -DskipTests"
    exit 1
fi

# 4. 启动进程（后台，日志重定向）
echo "Starting background process..."
nohup java -jar "$JAR_FILE" > hopaw-agent.out 2>&1 &
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
