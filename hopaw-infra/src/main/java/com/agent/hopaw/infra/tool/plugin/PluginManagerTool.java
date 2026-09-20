package com.agent.hopaw.infra.tool.plugin;

import com.agent.hopaw.infra.model.dto.PluginDescriptor;
import com.agent.hopaw.infra.model.dto.PluginInstallResult;
import com.agent.hopaw.infra.service.IAgentPluginService;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 插件管理工具：以**插件**（AgentPlugin）为粒度管理内置与插件能力。
 *
 * <p>一个插件可提供 0..N 个工具集，因此安装/卸载/列出都以插件为单位；
 * 工具集明细可通过 {@code agentToolSetTool} 查询。</p>
 *
 * <p>工具集名保留为 {@code pluginTool}（智能体绑定与前端 hook 按工具集名过滤）。</p>
 */
@Component("pluginManagerTool")
public class PluginManagerTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(PluginManagerTool.class);

    /** 允许的插件包最大字节数（100MB），避免一次性把超大文件读入内存 */
    private static final long MAX_PLUGIN_PACKAGE_SIZE = 100L * 1024L * 1024L;

    private final IAgentPluginService pluginService;

    public PluginManagerTool(IAgentPluginService pluginService) {
        this.pluginService = pluginService;
    }

    @Override
    public String getName() {
        return "pluginTool";
    }

    @Override
    public String getDescription() {
        return "插件管理工具：列出已安装插件、安装/升级/卸载插件。插件可包含多个工具集与前端组件";
    }

    @Override
    public String getIcon() {
        return "plugin-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "工具，插件，安装插件，卸载插件，管理插件";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {
            "列出已安装插件",
            "列出当前系统已安装的全部插件及其提供的工具集数量、版本与启停状态"
    })
    public String listPlugins() {
        List<PluginDescriptor> plugins = pluginService.getPlugins();
        if (plugins.isEmpty()) {
            return "成功：当前没有已安装插件";
        }
        StringBuilder sb = new StringBuilder("共 ").append(plugins.size()).append(" 个插件：\n");
        for (PluginDescriptor plugin : plugins) {
            sb.append("\n【插件】").append(plugin.getId());
            if (plugin.getName() != null && !plugin.getName().equals(plugin.getId())) {
                sb.append("（").append(plugin.getName()).append("）");
            }
            sb.append("\n");
            sb.append("版本：").append(plugin.getVersion() == null ? "未知" : plugin.getVersion()).append("\n");
            sb.append("状态：").append(plugin.isEnabled() ? "已启用" : "已禁用").append("\n");
            sb.append("说明：").append(plugin.getDescription() == null ? "" : plugin.getDescription()).append("\n");
            String[] toolSetNames = plugin.getToolSetNames();
            sb.append("工具集（").append(toolSetNames == null ? 0 : toolSetNames.length).append(" 个）：");
            if (toolSetNames == null || toolSetNames.length == 0) {
                sb.append("无（纯前端组件插件）");
            } else {
                sb.append(String.join("、", toolSetNames));
            }
            sb.append("\n");
            if (plugin.getFrontendAssetCount() > 0) {
                sb.append("前端组件：").append(plugin.getFrontendAssetCount()).append(" 个\n");
            }
            if (plugin.isInvokeSupported()) {
                sb.append("支持 invoke 调用\n");
            }
        }
        return "成功：\n" + sb;
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {
            "从本地文件安装插件",
            "从本地文件路径读取插件包并安装。文件应为 exportPlugin 导出的 zip 压缩包（包含插件清单 .json 和插件 .jar），"
                    + "或直接指定 .jar 插件文件"
    })
    public String installPluginFromLocal(@P(description = "本地插件包文件绝对路径，支持 exportPlugin 导出的 zip 压缩包或 .jar 文件") String filePath) {
        Path path;
        try {
            path = validatePluginPackage(filePath);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        String lowerName = path.getFileName().toString().toLowerCase();

        try {
            PluginInstallResult result;
            if (lowerName.endsWith(".jar")) {
                result = pluginService.installPluginFromJarFile(path);
            } else {
                byte[] bytes = Files.readAllBytes(path);
                result = pluginService.installPluginFromBytes(bytes);
            }
            return describeInstallResult(result);
        } catch (Exception e) {
            log.error("安装插件失败 path={}", path, e);
            return "安装失败：" + e.getMessage();
        }
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {
            "卸载插件",
            "按插件标识卸载已安装插件，可选择是否同时清理该插件及其所有工具集的配置项"
    })
    public String uninstallPlugin(@P(description = "插件标识（pluginId），可先用「列出已安装插件」查询") String pluginId,
                                  @P(description = "是否同时清理插件及其工具集的全部配置项，true=清理，false=保留", required = false) Boolean cleanConfig) {
        if (pluginId == null || pluginId.isBlank()) {
            return "卸载失败：插件标识不能为空，可先用「列出已安装插件」查询";
        }
        String id = pluginId.trim();
        if (pluginService.getPlugin(id) == null) {
            return "卸载失败：插件不存在 - " + id + "，可先用「列出已安装插件」查询";
        }
        boolean clean = Boolean.TRUE.equals(cleanConfig);
        try {
            boolean success = pluginService.unloadPlugin(id, clean);
            if (!success) {
                return "卸载失败：插件卸载未生效 - " + id;
            }
            return clean ? "卸载成功：" + id + "（配置项已清理）" : "卸载成功：" + id + "（配置项已保留）";
        } catch (Exception e) {
            log.error("卸载插件失败 pluginId={}", id, e);
            return "卸载失败：" + e.getMessage();
        }
    }

    /**
     * 校验插件包路径：非空、存在、可读、大小与扩展名合法。
     *
     * @return 规范化的 Path
     * @throws IllegalArgumentException 校验不通过，message 为对用户可读的失败原因
     */
    private Path validatePluginPackage(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("安装失败：文件路径不能为空");
        }

        Path path;
        try {
            path = Paths.get(filePath).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new IllegalArgumentException("安装失败：文件路径不合法 - " + e.getMessage());
        }

        File file = path.toFile();
        if (!file.exists()) {
            throw new IllegalArgumentException("安装失败：文件不存在 - " + path);
        }
        if (!file.isFile()) {
            throw new IllegalArgumentException("安装失败：路径不是文件 - " + path);
        }
        if (!file.canRead()) {
            throw new IllegalArgumentException("安装失败：文件不可读 - " + path);
        }
        if (file.length() <= 0L) {
            throw new IllegalArgumentException("安装失败：文件为空 - " + path);
        }
        if (file.length() > MAX_PLUGIN_PACKAGE_SIZE) {
            throw new IllegalArgumentException("安装失败：插件包超过最大允许大小（"
                    + (MAX_PLUGIN_PACKAGE_SIZE / 1024L / 1024L) + "MB） - " + path);
        }

        String lowerName = path.getFileName().toString().toLowerCase();
        if (!(lowerName.endsWith(".jar") || lowerName.endsWith(".zip"))) {
            throw new IllegalArgumentException("安装失败：插件包文件扩展名必须是 .jar 或 .zip（exportPlugin 导出的 zip 压缩包） - " + path);
        }
        return path;
    }

    private String describeInstallResult(PluginInstallResult result) {
        if (result == null) {
            return "安装失败：服务返回为空";
        }
        if (!result.isSuccess()) {
            StringBuilder sb = new StringBuilder("安装失败");
            if (result.getPluginId() != null) sb.append("：").append(result.getPluginId());
            if (result.getMessage() != null) sb.append(" - ").append(result.getMessage());
            return sb.toString();
        }
        StringBuilder sb = new StringBuilder("安装成功");
        sb.append("：").append(result.getPluginId() != null ? result.getPluginId() : "(未知插件标识)");
        if (result.getVersion() != null) {
            sb.append(" v").append(result.getVersion());
        }
        if (result.isUpgrade()) {
            sb.append("（升级自 ").append(result.getPreviousVersion() == null ? "未知版本" : result.getPreviousVersion()).append("）");
        }
        sb.append("，提供 ").append(result.getToolCount()).append(" 个工具集");
        if (result.getFileName() != null) {
            sb.append("，文件：").append(result.getFileName());
        }
        return sb.toString();
    }
}
