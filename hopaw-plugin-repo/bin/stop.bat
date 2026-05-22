@echo off
setlocal enabledelayedexpansion

REM 设置应用名称
set APP_NAME=hopaw-plugin-repo
set BIN_DIR=%~dp0

REM PID文件
set PID_FILE=%BIN_DIR%%APP_NAME%.pid

echo ========================================
echo %APP_NAME% 停止脚本
echo ========================================

REM 检查PID文件是否存在
if not exist "%PID_FILE%" (
    echo 错误: 找不到 PID 文件 %PID_FILE%
    echo %APP_NAME% 可能未运行
    pause
    exit /b 1
)

REM 读取PID
set /p TARGET_PID=<"%PID_FILE%"
echo 正在停止 %APP_NAME% (PID: %TARGET_PID%) ...

REM 检查进程是否存在
tasklist /FI "PID eq %TARGET_PID%" 2>NUL | find /I "java.exe" >NUL
if !ERRORLEVEL! NEQ 0 (
    echo 警告: 进程 %TARGET_PID% 不存在
    echo 清理 PID 文件
    del "%PID_FILE%"
    pause
    exit /b 1
)

REM 尝试优雅关闭 (通过Ctrl+C信号)
echo 发送关闭信号...
taskkill /PID %TARGET_PID% /T >NUL 2>&1

REM 等待进程结束
set MAX_WAIT=30
set WAIT_COUNT=0

:wait_loop
timeout /t 1 /nobreak >NUL
set /a WAIT_COUNT+=1

tasklist /FI "PID eq %TARGET_PID%" 2>NUL | find /I "java.exe" >NUL
if !ERRORLEVEL! NEQ 0 (
    echo %APP_NAME% 已成功停止
    del "%PID_FILE%"
    echo ========================================
    exit /b 0
)

if !WAIT_COUNT! GEQ !MAX_WAIT! (
    echo 优雅关闭超时，强制终止进程...
    taskkill /F /PID %TARGET_PID% /T >NUL 2>&1
    timeout /t 2 /nobreak >NUL
    
    tasklist /FI "PID eq %TARGET_PID%" 2>NUL | find /I "java.exe" >NUL
    if !ERRORLEVEL! NEQ 0 (
        echo %APP_NAME% 已强制停止
        del "%PID_FILE%"
    ) else (
        echo 错误: 无法停止进程 %TARGET_PID%
    )
    echo ========================================
    exit /b 0
)

goto :wait_loop

endlocal