#!/bin/bash
# 停止脚本：调用 run.sh 并传递 stop 参数
echo "正在调用 run.sh 并传递 stop 参数..."
bash "$(dirname "$0")/run.sh" stop
echo "run.sh 已执行完毕，当前脚本继续执行。"
