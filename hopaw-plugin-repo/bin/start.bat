@echo off
setlocal enabledelayedexpansion

REM 设置应用名称和JAR文件
set APP_NAME=hopaw-plugin-repo
set JAR_FILE=hopaw-plugin-repo-1.0.0.jar
set BIN_DIR=%~dp0
set APP_HOME=%BIN_DIR%..

REM 内存设置
set HEAP_SIZE=256m

REM PID文件
set PID_FILE=%BIN_DIR%%APP_NAME%.pid

REM 日志目录
set LOG_DIR=%APP_HOME%\logs
if not exist "%LOG_DIR%" (
    mkdir "%LOG_DIR%"
)

REM 日志文件
set LOG_FILE=%LOG_DIR%\%APP_NAME%.log

echo ========================================
echo %APP_NAME% 启动脚本
echo ========================================

REM 检查JAR文件是否存在
if not exist "%BIN_DIR%%JAR_FILE%" (
    echo 错误: 找不到 JAR 文件 %BIN_DIR%%JAR_FILE%
    pause
    exit /b 1
)

REM 检查是否已经在运行
if exist "%PID_FILE%" (
    set /p EXISTING_PID=<"%PID_FILE%"
    tasklist /FI "PID eq !EXISTING_PID!" 2>NUL | find /I "java.exe" >NUL
    if !ERRORLEVEL! EQU 0 (
        echo 警告: %APP_NAME% 已经在运行 (PID: !EXISTING_PID!)
        echo 如果要重新启动，请先运行 stop.bat
        pause
        exit /b 1
    ) else (
        echo 清理过期的 PID 文件
        del "%PID_FILE%"
    )
)

echo 正在启动 %APP_NAME% ...
echo JVM 参数: -Xms%HEAP_SIZE% -Xmx%HEAP_SIZE%

REM 启动应用
start /B java -Xms%HEAP_SIZE% -Xmx%HEAP_SIZE% -jar "%BIN_DIR%%JAR_FILE%" > "%LOG_FILE%" 2>&1
set NEW_PID=%!

REM 等待几秒让进程启动
timeout /t 3 /nobreak >NUL

REM 获取实际的PID
for /f "tokens=2" %%i in ('tasklist /FI "IMAGENAME eq java.exe" /FI "WINDOWTITLE eq %APP_NAME%*" /NH') do (
    set NEW_PID=%%i
    goto :found_pid
)

:found_pid
if defined NEW_PID (
    echo %NEW_PID% > "%PID_FILE%"
    echo %APP_NAME% 启动成功 (PID: %NEW_PID%)
    echo 日志文件: %LOG_FILE%
) else (
    echo 启动完成，请检查日志确认运行状态
    echo 日志文件: %LOG_FILE%
)

echo ========================================
endlocal