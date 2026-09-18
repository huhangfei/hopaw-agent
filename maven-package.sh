#!/bin/bash
# hopaw-agent Maven 打包脚本（Linux 版，Docker 分包部署第一步）
# 产出:
#   hopaw-app/target/hopaw-app-1.0.0.jar        应用自身类（普通 jar）
#   hopaw-app/target/hopaw-app-1.0.0-exec.jar   可执行 fat jar（本地 ./run.sh 用）
#   hopaw-app/target/docker-libs/*.jar          全部依赖 jar（模块 jar + 第三方）
# 之后执行 docker build 构建镜像

cd "$(dirname "$0")" || exit 1

echo "[1/2] Maven 构建全部模块 jar ..."
mvn clean package -DskipTests -q || { echo "[ERROR] Maven 构建失败"; exit 1; }

if [ ! -f hopaw-app/target/hopaw-app-1.0.0.jar ]; then
    echo "[ERROR] 未找到 hopaw-app/target/hopaw-app-1.0.0.jar"
    exit 1
fi

echo "[2/2] 收集依赖 jar 到 hopaw-app/target/docker-libs ..."
rm -rf hopaw-app/target/docker-libs
mvn dependency:copy-dependencies -q -pl hopaw-app \
    -DoutputDirectory=target/docker-libs \
    -DincludeScope=runtime \
    -DexcludeArtifactIds=spring-boot-devtools || { echo "[ERROR] 依赖收集失败"; exit 1; }

echo "完成！下一步: docker build -t hopaw-agent:1.0.0 ."
