#!/bin/bash

BIN_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "========================================"
echo "hopaw-plugin-repo 重启脚本"
echo "========================================"

# 先停止
"${BIN_DIR}/stop.sh"

# 等待一下
sleep 2

# 再启动
"${BIN_DIR}/start.sh"
