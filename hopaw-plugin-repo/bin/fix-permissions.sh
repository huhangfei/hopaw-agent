#!/bin/bash

# 修复 Docker 挂载目录权限问题

echo "========================================"
echo "修复 Docker 挂载目录权限"
echo "========================================"

# 获取当前脚本所在目录
BIN_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_HOME="$(dirname "$BIN_DIR")"

# 创建必要的目录
echo "创建必要的目录..."
mkdir -p "${APP_HOME}/data"
mkdir -p "${APP_HOME}/logs"
mkdir -p "${APP_HOME}/plugin-packages"

# 修改目录权限（777 允许所有用户读写）
echo "修改目录权限..."
chmod -R 777 "${APP_HOME}/data"
chmod -R 777 "${APP_HOME}/logs"
chmod -R 777 "${APP_HOME}/plugin-packages"

echo "✓ 目录权限修复完成"
echo "✓ data/: $(ls -ld ${APP_HOME}/data)"
echo "✓ logs/: $(ls -ld ${APP_HOME}/logs)"
echo "✓ plugin-packages/: $(ls -ld ${APP_HOME}/plugin-packages)"
echo "========================================"
echo ""
echo "现在可以启动 Docker 容器："
echo "  docker-compose up -d"
echo ""
