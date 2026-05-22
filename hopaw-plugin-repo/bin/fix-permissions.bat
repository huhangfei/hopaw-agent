@echo off
setlocal

REM 修复 Docker 挂载目录权限问题（Windows 版本）

echo ========================================
echo 修复 Docker 挂载目录权限
echo ========================================

REM 获取当前脚本所在目录
set BIN_DIR=%~dp0
set APP_HOME=%BIN_DIR%..

REM 创建必要的目录
echo 创建必要的目录...
if not exist "%APP_HOME%\data" mkdir "%APP_HOME%\data"
if not exist "%APP_HOME%\logs" mkdir "%APP_HOME%\logs"
if not exist "%APP_HOME%\plugin-packages" mkdir "%APP_HOME%\plugin-packages"

REM Windows 下不需要修改权限，Docker Desktop 会自动处理
echo ✓ 目录创建完成
echo ✓ data\: %APP_HOME%\data
echo ✓ logs\: %APP_HOME%\logs
echo ✓ plugin-packages\: %APP_HOME%\plugin-packages
echo ========================================
echo.
echo 现在可以启动 Docker 容器：
echo   docker-compose up -d
echo.
pause
