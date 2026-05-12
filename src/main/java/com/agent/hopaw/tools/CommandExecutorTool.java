package com.agent.hopaw.tools;

import com.agent.hopaw.service.AgentExecutorManager;
import com.agent.hopaw.util.InvocationParametersWrapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service("commandExecutor")
public class CommandExecutorTool implements AgentTool {

    private final AgentExecutorManager agentExecutorManager;
    private final Logger logger = LoggerFactory.getLogger(CommandExecutorTool.class);
    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LINES = 500;

    public CommandExecutorTool(AgentExecutorManager agentExecutorManager) {
        this.agentExecutorManager = agentExecutorManager;
    }

    @Tool("获取本地操作系统的名称，例如 Windows 10 或 Ubuntu 20.04")
    public String getOsName() {
        return System.getProperty("os.name");
    }

    @Tool("执行本地系统命令并返回输出结果。支持 Windows 和 Unix/Linux/macOS 系统。使用前最好先获取操作系统类型，" +
            "以确保命令在目标系统上执行。" +
            "请谨慎使用，避免执行危险命令如格式化磁盘、删除系统文件等。")
    public String executeCommand(@P(description = "要执行的命令") String command, @P(description = "超时时间（秒）", required = false) Integer timeout, InvocationParameters invocationParameters) {
        InvocationParametersWrapper invocationParametersWrapper = InvocationParametersWrapper.create(invocationParameters);
        String toolCallId = invocationParametersWrapper.getToolCallId();
        String userId = invocationParametersWrapper.getUserId();
        Long agentId = invocationParametersWrapper.getAgentId();
        logger.info("Executing command: {} with toolCallId: {}", command, toolCallId);
        if (command == null || command.trim().isEmpty()) {
            return "错误: 命令不能为空";
        }
//        if (isDangerousCommand(command)) {
//            return "错误: 检测到危险命令，已拒绝执行。出于安全考虑，不允许在用户未授权的情况下执行可能破坏系统的命令。";
//        }
        if (timeout == null) {
            timeout = TIMEOUT_SECONDS;
        }

        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder processBuilder;

            if (os.contains("win")) {
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                processBuilder = new ProcessBuilder("/bin/sh", "-c", command);
            }
            processBuilder.redirectErrorStream(true);
            //进程开始
            Process process = processBuilder.start();

            agentExecutorManager.addToolStopHook(agentId, userId, toolCallId, (callId) -> process.destroyForcibly());

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), determineEncoding(os)))) {
                //异步读取
                CompletableFuture<Void> outputReader = CompletableFuture.runAsync(() -> {
                    String line;
                    int lineCount = 0;
                    try {
                        while ((line = reader.readLine()) != null) {
                            if (agentExecutorManager.toolIsCancelled(agentId, userId, toolCallId)) {
                                output.append("错误: 命令执行被用户取消");
                                break;
                            }
                            if (lineCount >= MAX_OUTPUT_LINES) {
                                output.append("\n... (输出已截断，超过 ").append(MAX_OUTPUT_LINES).append(" 行)");
                                break;
                            }
                            agentExecutorManager.sendToolRunningContent(agentId, userId, toolCallId, line + "\n");
                            output.append(line).append("\n");
                            lineCount++;

                        }
                    } catch (Exception ex) {
                        logger.error("Error reading process output: {}", ex.getMessage(), ex);
                        output.append("错误: ").append(ex.getMessage());
                    }
                });
                if (process.waitFor(timeout, TimeUnit.SECONDS)) {
                    // 进程正常结束等待输出读取完成
                    outputReader.get(5, TimeUnit.SECONDS);
                    int exitCode= process.exitValue();
                    if (output.isEmpty()) {
                        return "命令执行成功 (退出码: " + exitCode + ")，无输出";
                    }
                    return "退出码: " + exitCode + "\n\n" + output;
                } else {
                    try {
                        ProcessHandle processHandle = process.toHandle();
                        processHandle.descendants()
                                .forEach(ProcessHandle::destroyForcibly);
                        processHandle.destroyForcibly();
                    } catch (Exception e) {
                        process.destroyForcibly(); // 降级方案
                    }
                    return "错误: 超时未执行完成，已强制退出。\n"+ (output.isEmpty() ? "本次无输出" :"输出："+ output);
                }
            }catch (TimeoutException e) {
                process.destroyForcibly();
                return "错误: 命令执行超时 (超过 " + timeout + " 秒)，已强制终止\n";
            } catch (IOException e) {
                // 进程被强制杀死时可能会触发 IOException，属于正常现象
                if (process.isAlive()){
                    //线程是活动就不正常
                    throw new RuntimeException(e);
                }
            }
        } catch (Exception e) {
            logger.error("Error executing command: {}", command, e);
            return "错误: 未知错误 - " + e.getMessage();
        }
        return "错误: 未知错误";
    }

    private Charset determineEncoding(String os) {
        if (os.contains("win")) {
            String codepage = System.getenv("LANG");
            if (codepage != null && codepage.toLowerCase().contains("utf")) {
                return StandardCharsets.UTF_8;
            }
            return Charset.forName("GBK");
        } else {
            return StandardCharsets.UTF_8;
        }
    }

    private boolean isDangerousCommand(String command) {
        String cmd = command.toLowerCase().trim();

        String[] dangerousPatterns = {
                "format", "del /f", "del /s", "rd /s", "rmdir /s",
                "rm -rf /", "rm -rf /*", "mkfs", "dd if=",
                "shutdown -s", "shutdown -r", "init 0", "init 6",
                ":(){:|:&};:", "> /dev/sda", "chmod -R 777 /"
        };

        for (String pattern : dangerousPatterns) {
            if (cmd.contains(pattern)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public String getName() {
        return "commandExecutor";
    }

    @Override
    public String getDescription() {
        return "执行本地系统命令";
    }

    @Override
    public String getIcon() {
        return "command-executor-tool";
    }

    @Override
    public String getKeyword() {
        return "本地命令";
    }
}
