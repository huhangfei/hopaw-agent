package com.agent.hopaw.biz.tool.plugin;

import com.agent.hopaw.infra.model.dto.PluginInstallResult;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.IAgentToolService;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.SearchBehavior;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 工具管理工具
 */
@Component("toolManagerTool")
public class ToolManagerTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ToolManagerTool.class);

    /** 允许的插件包最大字节数（100MB），避免一次性把超大文件读入内存 */
    private static final long MAX_PLUGIN_PACKAGE_SIZE = 100L * 1024L * 1024L;

    private final IAgentToolService agentToolService;

    public ToolManagerTool(IAgentToolService agentToolService) {
        this.agentToolService = agentToolService;
    }

    @Override
    public String getName() {
        return "pluginTool";
    }

    @Override
    public String getDescription() {
        return "工具管理工具，支持管理内置工具和插件工具，支持从本地 zip 压缩包或 .jar 文件安装插件工具";
    }

    @Override
    public String getIcon() {
        return "plugin-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "工具，插件，安装插件，管理工具";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {
            "从本地文件安装插件工具集",
            "从本地文件路径读取插件包并安装。文件应为 exportPlugin 导出的 zip 压缩包（包含 .json 清单和 .jar 插件）。"
    })
    public String installPluginToolFromLocal(@P(description = "本地插件包文件绝对路径，要求是 exportPlugin 导出的 zip 压缩包") String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "安装失败：文件路径不能为空";
        }

        Path path;
        try {
            path = Paths.get(filePath).toAbsolutePath().normalize();
        } catch (Exception e) {
            return "安装失败：文件路径不合法 - " + e.getMessage();
        }

        File file = path.toFile();
        if (!file.exists()) {
            return "安装失败：文件不存在 - " + path;
        }
        if (!file.isFile()) {
            return "安装失败：路径不是文件 - " + path;
        }
        if (!file.canRead()) {
            return "安装失败：文件不可读 - " + path;
        }

        long size = file.length();
        if (size <= 0L) {
            return "安装失败：文件为空 - " + path;
        }
        if (size > MAX_PLUGIN_PACKAGE_SIZE) {
            return "安装失败：插件包超过最大允许大小（" + (MAX_PLUGIN_PACKAGE_SIZE / 1024L / 1024L) + "MB） - " + path;
        }

        String lowerName = path.getFileName().toString().toLowerCase();
        if (!(lowerName.endsWith(".jar") || lowerName.endsWith(".zip"))) {
            return "安装失败：插件包文件扩展名必须是 .jar 或 .zip（exportPlugin 导出的 zip 压缩包） - " + path;
        }

        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (Exception e) {
            log.error("读取插件包失败 path={}", path, e);
            return "安装失败：读取文件出错 - " + e.getMessage();
        }

        try {
            PluginInstallResult result = agentToolService.installPluginFromBytes(bytes);
            if (result == null) {
                return "安装失败：服务返回为空";
            }
            if (!result.isSuccess()) {
                StringBuilder sb = new StringBuilder("安装失败");
                if (result.getToolName() != null) sb.append("：").append(result.getToolName());
                if (result.getMessage() != null) sb.append(" - ").append(result.getMessage());
                return sb.toString();
            }
            StringBuilder sb = new StringBuilder("安装成功");
            sb.append("：").append(result.getToolName() != null ? result.getToolName() : "(未知工具名)");
            if (result.getVersion() != null) {
                sb.append(" v").append(result.getVersion());
            }
            if (result.isUpgrade()) {
                sb.append("（升级自 ").append(result.getPreviousVersion() == null ? "未知版本" : result.getPreviousVersion()).append("）");
            }
            if (result.getToolCount() > 0) {
                sb.append("，新增 ").append(result.getToolCount()).append(" 个工具");
            }
            if (result.getFileName() != null) {
                sb.append("，文件：").append(result.getFileName());
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("安装插件失败 path={}", path, e);
            return "安装失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {
            "从本地JAR文件安装插件工具集",
            "直接读取本地 .jar 插件文件并安装（无需打包成 zip）。插件元数据会从 JAR 内部自动扫描。"
    })
    public String installPluginFromLocalJar(@P(description = "本地 .jar 插件文件的绝对路径") String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return "安装失败：文件路径不能为空";
        }

        Path path;
        try {
            path = Paths.get(filePath).toAbsolutePath().normalize();
        } catch (Exception e) {
            return "安装失败：文件路径不合法 - " + e.getMessage();
        }

        File file = path.toFile();
        if (!file.exists()) {
            return "安装失败：文件不存在 - " + path;
        }
        if (!file.isFile()) {
            return "安装失败：路径不是文件 - " + path;
        }
        if (!file.canRead()) {
            return "安装失败：文件不可读 - " + path;
        }
        if (file.length() <= 0L) {
            return "安装失败：文件为空 - " + path;
        }
        if (file.length() > MAX_PLUGIN_PACKAGE_SIZE) {
            return "安装失败：插件包超过最大允许大小（" + (MAX_PLUGIN_PACKAGE_SIZE / 1024L / 1024L) + "MB） - " + path;
        }
        if (!path.getFileName().toString().toLowerCase().endsWith(".jar")) {
            return "安装失败：文件扩展名必须为 .jar - " + path;
        }

        try {
            PluginInstallResult result = agentToolService.installPluginFromJarFile(path);
            if (result == null) {
                return "安装失败：服务返回为空";
            }
            if (!result.isSuccess()) {
                StringBuilder sb = new StringBuilder("安装失败");
                if (result.getToolName() != null) sb.append("：").append(result.getToolName());
                if (result.getMessage() != null) sb.append(" - ").append(result.getMessage());
                return sb.toString();
            }
            StringBuilder sb = new StringBuilder("安装成功");
            sb.append("：").append(result.getToolName() != null ? result.getToolName() : "(未知工具名)");
            if (result.getVersion() != null) {
                sb.append(" v").append(result.getVersion());
            }
            if (result.isUpgrade()) {
                sb.append("（升级自 ").append(result.getPreviousVersion() == null ? "未知版本" : result.getPreviousVersion()).append("）");
            }
            if (result.getToolCount() > 0) {
                sb.append("，新增 ").append(result.getToolCount()).append(" 个工具");
            }
            if (result.getFileName() != null) {
                sb.append("，文件：").append(result.getFileName());
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("从 JAR 安装插件失败 path={}", path, e);
            return "安装失败：" + e.getMessage();
        }
    }
}
