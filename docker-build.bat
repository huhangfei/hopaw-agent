@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM  hopaw-agent Docker 镜像构建脚本（分包部署第二步）
REM  前置条件: 已执行 maven-package.bat 产出分包构建
REM  用法:
REM    docker-build.bat              构建镜像 hopaw-agent:1.0.0
REM    docker-build.bat 1.0.1        构建指定 tag
REM    docker-build.bat 1.0.1 -save  构建并导出离线包 hopaw-agent-1.0.1.tar
REM
REM  分包结构: /app/app.jar（应用类） + /app/lib/*.jar（模块 jar + 依赖）
REM  单模块热更新: 只改某模块（如 hopaw-infra）时，重新构建该模块后
REM    copy /Y hopaw-infra\target\hopaw-infra-1.0.0.jar data\lib-override\
REM    docker restart hopaw-agent
REM ============================================================

set TAG=%~1
if "%TAG%"=="" set TAG=1.0.0
set IMAGE=hopaw-agent:%TAG%

REM 1. 检查分包构建产物是否就绪
if not exist hopaw-app\target\hopaw-app-1.0.0.jar (
    echo [错误] 未找到 hopaw-app\target\hopaw-app-1.0.0.jar
    echo 请先执行: maven-package.bat
    exit /b 1
)
if not exist hopaw-app\target\docker-libs (
    echo [错误] 未找到 hopaw-app\target\docker-libs 目录
    echo 请先执行: maven-package.bat
    exit /b 1
)

echo [1/2] 构建 Docker 镜像 %IMAGE% ...
docker build -t %IMAGE% .
if errorlevel 1 (
    echo [错误] Docker 镜像构建失败。
    exit /b 1
)

if /i "%~2"=="-save" (
    echo [2/2] 导出离线镜像包 hopaw-agent-%TAG%.tar ...
    docker save -o hopaw-agent-%TAG%.tar %IMAGE%
    if errorlevel 1 (
        echo [错误] 镜像导出失败。
        exit /b 1
    )
    echo 离线包已生成: hopaw-agent-%TAG%.tar
    echo 目标机器导入: docker load -i hopaw-agent-%TAG%.tar
) else (
    echo [2/2] 跳过离线包导出（如需导出请加 -save 参数）
)

echo.
echo 完成！镜像: %IMAGE%
echo 启动: docker-compose up -d    （数据持久化在 ./data 目录）
endlocal
