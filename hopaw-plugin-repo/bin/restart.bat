@echo off
setlocal

set BIN_DIR=%~dp0

echo ========================================
echo hopaw-plugin-repo 重启脚本
echo ========================================

REM 先停止
call "%BIN_DIR%stop.bat"

REM 等待一下
timeout /t 2 /nobreak >NUL

REM 再启动
call "%BIN_DIR%start.bat"

endlocal