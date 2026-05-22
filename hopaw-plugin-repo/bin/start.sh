#!/bin/bash

# 设置应用名称和JAR文件
APP_NAME="hopaw-plugin-repo"
JAR_FILE="hopaw-plugin-repo-1.0.0.jar"
BIN_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="$(dirname "$BIN_DIR")"

# 内存设置
HEAP_SIZE="256m"

# PID文件
PID_FILE="${BIN_DIR}/${APP_NAME}.pid"

# 日志目录
LOG_DIR="${APP_HOME}/logs"
mkdir -p "${LOG_DIR}"

# 日志文件
LOG_FILE="${LOG_DIR}/${APP_NAME}.log"

echo "========================================"
echo "${APP_NAME} 启动脚本"
echo "========================================"

# 检查JAR文件是否存在
if [ ! -f "${BIN_DIR}/${JAR_FILE}" ]; then
    echo "错误: 找不到 JAR 文件 ${BIN_DIR}/${JAR_FILE}"
    exit 1
fi

# 检查是否已经在运行
if [ -f "${PID_FILE}" ]; then
    EXISTING_PID=$(cat "${PID_FILE}")
    if ps -p "${EXISTING_PID}" > /dev/null 2>&1; then
        echo "警告: ${APP_NAME} 已经在运行 (PID: ${EXISTING_PID})"
        echo "如果要重新启动，请先运行 stop.sh"
        exit 1
    else
        echo "清理过期的 PID 文件"
        rm -f "${PID_FILE}"
    fi
fi

echo "正在启动 ${APP_NAME} ..."
echo "JVM 参数: -Xms${HEAP_SIZE} -Xmx${HEAP_SIZE}"

# 启动应用
nohup java -Xms${HEAP_SIZE} -Xmx${HEAP_SIZE} -jar "${BIN_DIR}/${JAR_FILE}" > "${LOG_FILE}" 2>&1 &
NEW_PID=$!

# 等待几秒让进程启动
sleep 3

# 检查进程是否成功启动
if ps -p "${NEW_PID}" > /dev/null 2>&1; then
    echo "${NEW_PID}" > "${PID_FILE}"
    echo "${APP_NAME} 启动成功 (PID: ${NEW_PID})"
    echo "日志文件: ${LOG_FILE}"
else
    echo "启动失败，请检查日志"
    echo "日志文件: ${LOG_FILE}"
    exit 1
fi

echo "========================================"
