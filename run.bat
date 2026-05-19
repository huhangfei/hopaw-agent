@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

set JAR_FILE=hopaw-app\target\hopaw-app-1.0.0.jar
set PORT=8080
set PID_FILE=%~dp0hopaw-agent.pid
set TIMEOUT=30

:: 1. 处理停止命令
if /i "%~1"=="stop" goto :stop

echo ============================================
echo   Hopaw Agent Starting...
echo ============================================

:: 2. 检查 Java 环境
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java not found in PATH.
    pause
    exit /b 1
)

:: 3. 检查 JAR 文件
if not exist "%JAR_FILE%" (
    echo [ERROR] %JAR_FILE% not found
    echo Please run: mvn package -DskipTests
    pause
    exit /b 1
)


:: 5. 启动进程
echo Starting background process...
powershell -Command "$p = Start-Process javaw -ArgumentList '-jar', '%JAR_FILE%' -PassThru; [System.IO.File]::WriteAllText('%PID_FILE%', $p.Id.ToString())"

:: 6. 等待端口监听（带超时）
echo Waiting for port %PORT% ...
set /a count=0
:wait
timeout /t 2 /nobreak >nul
set /a count+=1
if !count! geq %TIMEOUT% (
    echo [ERROR] Startup timed out. Check logs for details.
    exit /b 1
)

powershell -Command "try { Invoke-WebRequest -Uri http://localhost:%PORT% -TimeoutSec 2 -UseBasicParsing | Out-Null; exit 0 } catch { exit 1 }" >nul 2>&1
if %errorlevel% neq 0 goto wait

:: 7. 打开浏览器并退出
echo Opening browser...
start "" http://localhost:%PORT%

echo ============================================
echo   Ready. Browser opened.
echo   You may close this window.
echo   Use 'run.bat stop' kill the %PORT%
echo ============================================
timeout /t 3 /nobreak >nul
exit

:: 停止逻辑
:stop
echo Killing process on port %PORT%...

for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%PORT% ^| findstr LISTENING') do (
    echo Found PID: %%a, terminating...
    taskkill /PID %%a /F
)

echo Done.
pause