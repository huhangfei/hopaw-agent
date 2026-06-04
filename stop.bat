@echo off
echo 正在调用 run.bat 并传递 stop 参数...

:: 使用 call 命令调用同目录下的 run.bat，并将 stop 作为参数传递
call run.bat stop

echo run.bat 已执行完毕，当前脚本继续执行。
pause