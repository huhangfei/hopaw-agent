package com.agent.hopaw.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Service("commandExecutor")
public class CommandExecutorTool implements AgentTool {

    private static final int TIMEOUT_SECONDS = 30;
    private static final int MAX_OUTPUT_LINES = 500;

    @Tool("获取本地操作系统的名称，例如 Windows 10 或 Ubuntu 20.04")
    public String getOsName() {
        return System.getProperty("os.name");
    }

    @Tool("执行本地系统命令并返回输出结果。支持 Windows 和 Unix/Linux/macOS 系统。使用前最好先获取操作系统类型，" +
          "以确保命令在目标系统上执行。" +
          "请谨慎使用，避免执行危险命令如格式化磁盘、删除系统文件等。")
    public String executeCommand(@P(description="要执行的命令") String command, @P(description = "超时时间（秒）",required = false) Integer timeout) {
        if (command == null || command.trim().isEmpty()) {
            return "错误: 命令不能为空";
        }
//        if (isDangerousCommand(command)) {
//            return "错误: 检测到危险命令，已拒绝执行。出于安全考虑，不允许在用户未授权的情况下执行可能破坏系统的命令。";
//        }
        if(timeout==null){
            timeout=TIMEOUT_SECONDS;
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

            Process process = processBuilder.start();
            boolean completed = process.waitFor(timeout, TimeUnit.SECONDS);

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), determineEncoding(os)))) {
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null) {
                    if (lineCount >= MAX_OUTPUT_LINES) {
                        output.append("\n... (输出已截断，超过 ").append(MAX_OUTPUT_LINES).append(" 行)");
                        break;
                    }
                    output.append(line).append("\n");
                    lineCount++;
                }
            }

            if (!completed) {
                process.destroyForcibly();
                return "错误: 命令执行超时 (超过 " + timeout + " 秒)，已强制终止";
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (result.isEmpty()) {
                return "命令执行成功 (退出码: " + exitCode + ")，无输出";
            }

            return "退出码: " + exitCode + "\n\n" + result;

        } catch (IOException e) {
            return "错误: 执行命令失败 - " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "错误: 命令执行被中断";
        } catch (Exception e) {
            return "错误: 未知错误 - " + e.getMessage();
        }
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
}
