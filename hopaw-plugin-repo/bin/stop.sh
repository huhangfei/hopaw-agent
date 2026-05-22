#!/bin/bash

# 设置应用名称
APP_NAME="hopaw-plugin-repo"
BIN_DIR="$(cd "$(dirname "$0")" && pwd)"

# PID文件
PID_FILE="${BIN_DIR}/${APP_NAME}.pid"

echo "========================================"
echo "${APP_NAME} 停止脚本"
echo "========================================"

# 检查PID文件是否存在
if [ ! -f "${PID_FILE}" ]; then
    echo "错误: 找不到 PID 文件 ${PID_FILE}"
    echo "${APP_NAME} 可能未运行"
    exit 1
fi

# 读取PID
TARGET_PID=$(cat "${PID_FILE}")
echo "正在停止 ${APP_NAME} (PID: ${TARGET_PID}) ..."

# 检查进程是否存在
if ! ps -p "${TARGET_PID}" > /dev/null 2>&1; then
    echo "警告: 进程 ${TARGET_PID} 不存在"
    echo "清理 PID 文件"
    rm -f "${PID_FILE}"
    exit 1
fi

# 尝试优雅关闭 (发送SIGTERM信号)
echo "发送关闭信号..."
kill "${TARGET_PID}" 2>/dev/null

# 等待进程结束
MAX_WAIT=30
WAIT_COUNT=0

while [ ${WAIT_COUNT} -lt ${MAX_WAIT} ]; do
    sleep 1
    WAIT_COUNT=$((WAIT_COUNT + 1))
    
    if ! ps -p "${TARGET_PID}" > /dev/null 2>&1; then
        echo "${APP_NAME} 已成功停止"
        rm -f "${PID_FILE}"
        echo "========================================"
        exit 0
    fi
done

# 超时后强制终止
echo "优雅关闭超时，强制终止进程..."
kill -9 "${TARGET_PID}" 2>/dev/null
sleep 2

if ! ps -p "${TARGET_PID}" > /dev/null 2>&1; then
    echo "${APP_NAME} 已强制停止"
    rm -f "${PID_FILE}"
else
    echo "错误: 无法停止进程 ${TARGET_PID}"
fi

echo "========================================"
