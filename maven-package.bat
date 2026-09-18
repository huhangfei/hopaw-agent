@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM  hopaw-agent Maven 打包脚本（Docker 分包部署第一步）
REM  产出:
REM    hopaw-app\target\hopaw-app-1.0.0.jar        应用自身类（普通 jar）
REM    hopaw-app\target\hopaw-app-1.0.0-exec.jar   可执行 fat jar（本地 run.bat 用）
REM    hopaw-app\target\docker-libs\*.jar          全部依赖 jar（模块 jar + 第三方）
REM  之后执行 docker-build.bat 构建镜像
REM ============================================================

echo [1/2] Maven 构建全部模块 jar ...
call mvn clean package -DskipTests -q
if errorlevel 1 (
    echo [错误] Maven 构建失败，请检查编译错误。
    exit /b 1
)

if not exist hopaw-app\target\hopaw-app-1.0.0.jar (
    echo [错误] 未找到 hopaw-app\target\hopaw-app-1.0.0.jar
    exit /b 1
)

echo [2/2] 收集依赖 jar 到 hopaw-app\target\docker-libs ...
if exist hopaw-app\target\docker-libs rmdir /s /q hopaw-app\target\docker-libs
call mvn dependency:copy-dependencies -q -pl hopaw-app -DoutputDirectory=target\docker-libs -DincludeScope=runtime -DexcludeArtifactIds=spring-boot-devtools
if errorlevel 1 (
    echo [错误] 依赖收集失败。
    exit /b 1
)

echo.
echo 完成！产物:
echo   hopaw-app\target\hopaw-app-1.0.0.jar        （应用类）
echo   hopaw-app\target\hopaw-app-1.0.0-exec.jar   （本地可执行）
echo   hopaw-app\target\docker-libs\*.jar          （依赖库）
echo 下一步: docker-build.bat 构建镜像
endlocal
