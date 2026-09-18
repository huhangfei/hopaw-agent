package com.agent.hopaw.tool.demo;

import com.agent.hopaw.infra.model.dto.OptionItem;
import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.model.dto.ToolMapConfigItem;
import com.agent.hopaw.infra.model.dto.ValidationRule;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.tool.AgentTool;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DemoTool implements AgentTool {

    private static final Logger logger = LoggerFactory.getLogger(DemoTool.class);

    private static final String CONFIG_KEY_ACCOUNTS = "accounts";

    @Autowired
    private ISysConfigService sysConfigService;

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

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询系统状态", "查询当前系统状态信息，包括JVM内存、磁盘空间、系统时间等"})
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

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询系统属性", "查询指定JVM系统属性值，如java.version、os.name等"})
    public String querySystemProperty(
            @P("系统属性名，如java.version、os.name、user.dir") String key) {
        String value = System.getProperty(key);
        if (value == null) {
            return "未找到系统属性: " + key + "\n常用属性名: java.version, java.home, os.name, user.dir, file.encoding";
        }
        logger.info("DemoTool: queried system property [{}]={}", key, value);
        return key + " = " + value;
    }

    /**
     * 演示 MAP 映射组结构配置：多组账号，每组一套同构子配置。
     * 同时作为插件读取映射组配置的参考实现（列表键 + 散键）。
     */
    @Override
    public List<ToolConfigItem> getConfigItems() {
        ToolMapConfigItem accounts = new ToolMapConfigItem(
                CONFIG_KEY_ACCOUNTS, "账号名称", "演示映射组结构：可添加多组账号，每组一套配置（组名唯一，不含英文逗号冒号）",
                ToolConfigItem.ConfigType.TEXT_SINGLE);
        accounts.setValues(List.of(
                new ToolConfigItem("apiKey", "API密钥", "该账号的API密钥", ToolConfigItem.ConfigType.TEXT_SINGLE)
                        .validation(new ValidationRule().required()),
                new ToolConfigItem("secret", "密码", "该账号的密码（掩码显示）", ToolConfigItem.ConfigType.TEXT_PASSWORD),
                new ToolConfigItem("level", "级别", "账号级别", ToolConfigItem.ConfigType.SELECT,
                        new OptionItem("basic", "基础版"), new OptionItem("pro", "专业版"))
                        .sensitive(false)
        ));
        return List.of(accounts);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"查询演示账号", "查询映射组结构配置的多组演示账号信息（每组含API密钥、密码、级别）"})
    public String queryDemoAccounts() {
        String prefix = getConfigPrefix();
        SysConfig listConfig = sysConfigService.getByKey(prefix + CONFIG_KEY_ACCOUNTS);
        String json = listConfig != null ? listConfig.getConfigValue() : null;
        if (json == null || json.isBlank()) {
            return "尚未配置任何演示账号";
        }
        // MAP 结构存储：主体键的值为 JSON 对象 {"组名":{"子配置key":"值",...},...}
        LinkedHashMap<String, LinkedHashMap<String, String>> accounts;
        try {
            accounts = JSON.parseObject(json, new TypeReference<LinkedHashMap<String, LinkedHashMap<String, String>>>() {});
        } catch (Exception e) {
            return "配置解析失败：" + e.getMessage();
        }
        StringBuilder sb = new StringBuilder("=== 演示账号配置 ===\n");
        for (Map.Entry<String, LinkedHashMap<String, String>> entry : accounts.entrySet()) {
            String apiKey = entry.getValue().get("apiKey");
            String level = entry.getValue().get("level");
            sb.append("账号[").append(entry.getKey()).append("] 级别: ").append(level)
                    .append("，密钥: ").append(apiKey == null || apiKey.isEmpty() ? "未配置" : apiKey)
                    .append("\n");
        }
        return sb.toString();
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }
}