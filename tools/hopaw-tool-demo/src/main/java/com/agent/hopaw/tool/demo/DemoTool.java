package com.agent.hopaw.tool.demo;

import com.agent.hopaw.infra.tool.AgentTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DemoTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(DemoTool.class);

    @Override
    public String getName() {
        return "demoPluginTool";
    }

    @Override
    public String getDescription() {
        return "插件式演示工具，提供系统信息查询功能（动态加载）";
    }

    @Override
    public String getKeyword() {
        return "系统,信息,状态";
    }

    @Tool("查询当前系统状态信息，包括JVM内存、磁盘空间、系统时间等")
    public String querySystemStatus() {
        StringBuilder sb = new StringBuilder();
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        long usedMemory = totalMemory - freeMemory;

        sb.append("=== 系统状态 ===\n");
        sb.append("当前时间: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n");
        sb.append("处理器数: ").append(runtime.availableProcessors()).append("核\n");
        sb.append("JVM最大内存: ").append(maxMemory).append("MB\n");
        sb.append("JVM已分配内存: ").append(totalMemory).append("MB\n");
        sb.append("JVM已使用内存: ").append(usedMemory).append("MB\n");
        sb.append("JVM空闲内存: ").append(freeMemory).append("MB\n");

        File[] roots = File.listRoots();
        for (File root : roots) {
            sb.append("磁盘 ").append(root.getPath()).append(": ")
                    .append("总空间 ").append(root.getTotalSpace() / 1024 / 1024 / 1024).append("GB, ")
                    .append("可用空间 ").append(root.getFreeSpace() / 1024 / 1024 / 1024).append("GB\n");
        }

        logger.info("DemoTool: system status queried");
        return sb.toString();
    }

    @Tool("查询指定JVM系统属性值，如java.version、os.name等")
    public String querySystemProperty(
            @P("系统属性名，如java.version、os.name、user.dir") String key) {
        String value = System.getProperty(key);
        if (value == null) {
            return "未找到系统属性: " + key + "\n常用属性名: java.version, java.home, os.name, user.dir, file.encoding";
        }
        logger.info("DemoTool: queried system property [{}]={}", key, value);
        return key + " = " + value;
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }
}