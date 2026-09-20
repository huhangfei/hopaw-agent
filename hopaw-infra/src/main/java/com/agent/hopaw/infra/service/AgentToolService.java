package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.constant.AgentToolSourceEnum;
import com.agent.hopaw.infra.event.ConfigChangeEvent;
import com.agent.hopaw.infra.model.dto.PluginExportInfo;
import com.agent.hopaw.infra.model.dto.PluginInstallResult;
import com.agent.hopaw.infra.model.dto.PluginUpdateInfo;
import com.agent.hopaw.infra.model.dto.PluginConflictInfo;
import com.agent.hopaw.infra.model.dto.ToolInfo;
import com.agent.hopaw.infra.model.dto.ToolParamInfo;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.plugin.PluginRegistry;
import com.agent.hopaw.infra.plugin.AgentPlugin;
import com.agent.hopaw.infra.plugin.JarPluginLoader;
import com.agent.hopaw.infra.tool.AgentTool;
import com.agent.hopaw.infra.tool.IAgentToolService;
import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class AgentToolService implements IAgentToolService {
    private static final Logger log = LoggerFactory.getLogger(AgentToolService.class);
    private static final int BUFFER_SIZE = 8192;

    private final ApplicationContext applicationContext;
    private final PluginRegistry pluginRegistry;
    private final JarPluginLoader jarPluginLoader;
    private final ObjectMapper objectMapper;

    public AgentToolService(ApplicationContext applicationContext, PluginRegistry pluginRegistry, JarPluginLoader jarPluginLoader) {
        this.applicationContext = applicationContext;
        this.pluginRegistry = pluginRegistry;
        this.jarPluginLoader = jarPluginLoader;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<AgentTool> getAgentTools() {
        Map<String, AgentTool> beans = applicationContext.getBeansOfType(AgentTool.class);
        List<AgentTool> tools = new ArrayList<>(beans.values());
        tools.addAll(pluginRegistry.getAllPluginTools());
        tools.sort(Comparator.comparing(AgentTool::getName));
        return tools;
    }

    @EventListener
    public void onConfigChange(ConfigChangeEvent event) {
        List<AgentTool> allTools = getAgentTools();
        for (AgentTool tool : allTools) {
            String prefix = tool.getConfigPrefix();
            for (String key : event.getChangedKeys()) {
                if (key.startsWith(prefix)) {
                    try {
                        tool.onConfigChanged();
                        log.info("Config changed for tool [{}], onConfigChanged called", tool.getName());
                    } catch (Exception e) {
                        log.error("Error calling onConfigChanged for tool [{}]", tool.getName(), e);
                    }
                    break;
                }
            }
        }
    }

    @Override
    public List<ToolSetInfo> getToolSets() {
        List<ToolSetInfo> result = new ArrayList<>();
        Map<String, AgentTool> beans = applicationContext.getBeansOfType(AgentTool.class);
        for (AgentTool agentTool : beans.values()) {
            result.add(scanToolSet(agentTool, AgentToolSourceEnum.BUILT_IN, null));
        }
        result.addAll(getAllPluginToolSets());
        return result;
    }

    @Override
    public Map<String, String> getToolNameAndDescriptionMap() {
        Map<String,String> toolNameMap = new HashMap<>();
        getToolSets().forEach(toolSet -> toolSet.getTools().forEach(
                tool -> toolNameMap.put(tool.getName(), tool.getDescriptions().size()>0 ? tool.getDescriptions().get(0) : tool.getName())
        ));
        toolNameMap.put(AgentTool.TOOL_SEARCH_TOOL_NAME, AgentTool.TOOL_SEARCH_TOOL_DESCRIPTION);
        return toolNameMap;
    }

    private List<ToolSetInfo> getAllPluginToolSets() {
        List<ToolSetInfo> result = new ArrayList<>();
        List<PluginRegistry.PluginEntry> allPluginEntries = pluginRegistry.getAllPluginEntries();
        for (PluginRegistry.PluginEntry entry : allPluginEntries) {
            for (AgentTool tool : entry.getTools()) {
                ToolSetInfo toolSetInfo = scanToolSet(tool, AgentToolSourceEnum.PLUGIN, entry);
                toolSetInfo.setJarFileName(entry.getJarFileName());
                toolSetInfo.setPluginId(entry.getPluginId());
                toolSetInfo.setPluginName(entry.getPluginName());
                if(!AgentTool.DEFAULT_ICON.equals(toolSetInfo.getIcon())){
                    String iconContent = entry.getCachedResource("static/icons/tools/" + toolSetInfo.getIcon());
                    if (iconContent != null && !iconContent.isEmpty()) {
                        toolSetInfo.setIcon(iconContent);
                    }
                }
                result.add(toolSetInfo);
            }
        }
        return result;
    }

    /**
     * 扫描工具集元数据。
     *
     * @param agentTool 工具实例
     * @param source    来源（内置 / 插件）
     * @param entry     所属插件条目；内置工具传 null
     */
    private ToolSetInfo scanToolSet(AgentTool agentTool, AgentToolSourceEnum source, PluginRegistry.PluginEntry entry) {
        List<ToolInfo> tools = new ArrayList<>();
        for (Method method : agentTool.getClass().getMethods()) {
            Tool toolAnn = method.getAnnotation(Tool.class);
            if (toolAnn == null) continue;

            String toolName = toolAnn.name();
            if (toolName.isEmpty()) {
                toolName = method.getName();
            }
            String description = Arrays.stream(toolAnn.value()).collect(Collectors.joining(" "));
            List<ToolParamInfo> params = new ArrayList<>();
            for (Parameter param : method.getParameters()) {
                if (param.getType() == InvocationParameters.class) continue;

                P pAnn = param.getAnnotation(P.class);
                String paramName = param.getName();
                String paramDesc = "";
                boolean required = true;

                if (pAnn != null) {
                    paramDesc = pAnn.description();
                    if (paramDesc.isEmpty()) {
                        paramDesc = pAnn.value();
                    }
                    required = pAnn.required();
                }

                params.add(new ToolParamInfo(paramName, paramDesc, required, param.getType().getSimpleName()));
            }
            params.sort(Comparator.comparing(p -> p.isRequired() ? 0 : 1));

            ToolInfo toolInfo = new ToolInfo(toolName, description, params);
            toolInfo.setDescriptions(Arrays.asList(toolAnn.value()));
            ToolSecurityLevel methodSecurityAnn = method.getAnnotation(ToolSecurityLevel.class);
            if (methodSecurityAnn != null) {
                toolInfo.setSecurityLevel(methodSecurityAnn.value());
            }
            tools.add(toolInfo);
        }
        ToolSetInfo toolSetInfo = new ToolSetInfo(agentTool.getName(), agentTool.getDescription(),
                resolveIcon(agentTool, entry == null ? null : entry.getPlugin()), tools, source);
        // 插件身份元数据（版本/作者/来源）取自插件主体，工具不再承载；内置工具用默认值
        AgentPlugin plugin = entry == null ? null : entry.getPlugin();
        if (plugin != null) {
            toolSetInfo.setVersion(plugin.getVersion());
            toolSetInfo.setAuthor(plugin.getAuthor());
            toolSetInfo.setUrl(plugin.getUrl());
            toolSetInfo.setKeyword(agentTool.getKeyword().isEmpty() ? plugin.getKeyword() : agentTool.getKeyword());
        } else {
            toolSetInfo.setVersion("1.0.0");
            toolSetInfo.setAuthor("Agent Tool");
            toolSetInfo.setUrl("https://gitee.com/hgflydream/hopaw-agent");
            toolSetInfo.setKeyword(agentTool.getKeyword());
        }
        toolSetInfo.setHasConfigItems(!agentTool.getConfigItems().isEmpty());
        toolSetInfo.setAgentTool(agentTool);

        return toolSetInfo;
    }

    /**
     * 解析工具集图标：优先工具自身声明，工具未声明（默认图标）时回退到所属插件声明的图标。
     */
    private String resolveIcon(AgentTool agentTool, AgentPlugin plugin) {
        String icon = agentTool.getIcon();
        if (AgentTool.DEFAULT_ICON.equals(icon) && plugin != null && plugin.getIcon() != null) {
            return plugin.getIcon();
        }
        return icon;
    }

    @Override
    public boolean unloadPlugin(String jarFileName) {
        return jarPluginLoader.unloadAndDeletePlugin(jarFileName);
    }

    @Override
    public PluginInstallResult installOrUpgradePlugin(PluginUpdateInfo updateInfo) {
        return installOrUpgradePlugin(updateInfo, null, null);
    }

    @Override
    public PluginInstallResult installOrUpgradePlugin(PluginUpdateInfo updateInfo,
                                                      java.util.function.Consumer<String> stageCallback,
                                                      java.util.function.Consumer<Integer> downloadProgressCallback) {
        String toolName = updateInfo.getToolName();
        String version = updateInfo.getVersion();
        String jarFileName = updateInfo.getFileName();
        
        if (jarFileName == null || !jarFileName.toLowerCase().endsWith(".jar")) {
            jarFileName = toolName + ".jar";
        }
        
        if (updateInfo.getDownloadUrl() == null || updateInfo.getDownloadUrl().isEmpty()) {
            log.error("Download URL is empty for plugin: {}", toolName);
            return PluginInstallResult.fail(toolName, version, jarFileName, "下载地址为空");
        }

        if (updateInfo.getSha256Hash() == null || updateInfo.getSha256Hash().isEmpty()) {
            log.error("SHA256 hash is empty for plugin: {}", toolName);
            return PluginInstallResult.fail(toolName, version, jarFileName, "插件哈希值为空");
        }

        Path pluginDir = jarPluginLoader.getPluginDir();
        Path targetPath = pluginDir.resolve(jarFileName);
        String previousVersion = updateInfo.getCurrentVersion();
        boolean isUpgrade = updateInfo.isInstalled();

        try {
            if (isUpgrade) {
                log.info("Plugin {} is installed, uninstalling before upgrade", toolName);
                reportStage(stageCallback, "uninstalling");
                // unregister 内部会统一调用各工具的 destroy 并关闭 classloader，无需重复 destroy
                pluginRegistry.unregisterByJarFileName(jarFileName);
                File existingFile = targetPath.toFile();
                if (existingFile.exists() && !existingFile.delete()) {
                    log.warn("Failed to delete existing plugin file: {}", targetPath);
                }
            }

            log.info("Downloading plugin ZIP from: {}", updateInfo.getDownloadUrl());
            reportStage(stageCallback, "downloading");
            URL downloadUrl = new URI(updateInfo.getDownloadUrl()).toURL();
            HttpURLConnection connection = (HttpURLConnection) downloadUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(600000);
            connection.setReadTimeout(3000000);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                log.error("Failed to download plugin ZIP, HTTP response code: {}", responseCode);
                connection.disconnect();
                return PluginInstallResult.fail(toolName, version, jarFileName, 
                    String.format("下载失败，HTTP响应码: %d", responseCode));
            }

            long totalBytes = connection.getContentLengthLong();
            Path tempZip = Files.createTempFile("plugin_download_", ".zip");
            try (InputStream in = connection.getInputStream();
                 java.io.OutputStream out = Files.newOutputStream(tempZip)) {
                byte[] buffer = new byte[BUFFER_SIZE];
                long bytesRead = 0;
                int n;
                int lastReportedPercent = 0;
                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    bytesRead += n;
                    if (totalBytes > 0) {
                        int percent = (int) (bytesRead * 100 / totalBytes);
                        if (percent > lastReportedPercent) {
                            lastReportedPercent = percent;
                            reportProgress(downloadProgressCallback, percent);
                        }
                    }
                }
                reportProgress(downloadProgressCallback, 100);
            }
            connection.disconnect();

            reportStage(stageCallback, "extracting");
            byte[] jarBytes = null;
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().endsWith(".jar")) {
                        jarBytes = zis.readAllBytes();
                    }
                    zis.closeEntry();
                }
            }

            if (jarBytes == null) {
                log.error("ZIP包中未找到插件JAR文件");
                Files.deleteIfExists(tempZip);
                return PluginInstallResult.fail(toolName, version, jarFileName, "ZIP包中未找到插件JAR文件");
            }

            reportStage(stageCallback, "verifying");
            String downloadedJarHash = calculateSHA256(jarBytes);
            if (!updateInfo.getSha256Hash().equalsIgnoreCase(downloadedJarHash)) {
                log.error("JAR文件SHA256哈希校验失败！期望: {}, 实际: {}",
                        updateInfo.getSha256Hash(), downloadedJarHash);
                Files.deleteIfExists(tempZip);
                return PluginInstallResult.fail(toolName, version, jarFileName, "插件哈希校验失败");
            }

            reportStage(stageCallback, "installing");
            Files.write(targetPath, jarBytes);
            log.info("Plugin {} installed successfully to {}, JAR哈希校验通过", toolName, targetPath);

            PluginConflictInfo conflictInfo = detectConflicts(targetPath.toFile(), toolName);

            int toolCount = jarPluginLoader.loadPlugin(targetPath.toFile());

            Files.deleteIfExists(tempZip);

            return PluginInstallResult.success(toolName, version, jarFileName, toolCount, isUpgrade, previousVersion, conflictInfo);
        } catch (Exception e) {
            log.error("Error installing/upgrading plugin: {}", toolName, e);
            return PluginInstallResult.fail(toolName, version, jarFileName, 
                String.format("安装失败: %s", e.getMessage()));
        }
    }

    private void reportStage(java.util.function.Consumer<String> callback, String stage) {
        if (callback != null) {
            callback.accept(stage);
        }
    }

    private void reportProgress(java.util.function.Consumer<Integer> callback, int percent) {
        if (callback != null) {
            callback.accept(percent);
        }
    }

    private String calculateSHA256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String calculateSHA256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(data);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hashBytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private PluginConflictInfo detectConflicts(File jarFile, String currentPluginId) {
        List<String> conflictingPlugins = new ArrayList<>();
        List<String> conflictingTools = new ArrayList<>();

        JarPluginLoader.PluginScanResult scanResult = jarPluginLoader.scanPluginInfo(jarFile);
        if (scanResult.hasError() || scanResult.pluginId == null) {
            return null;
        }

        Map<String, PluginRegistry.PluginEntry> allPlugins = pluginRegistry.getPlugins();
        for (PluginRegistry.PluginEntry entry : allPlugins.values()) {
            if (!entry.getJarFileName().equals(jarFile.getName())
                    && scanResult.pluginId.equals(entry.getPluginId())) {
                conflictingPlugins.add(entry.getPluginId());
            }
        }

        if (scanResult.toolNames != null) {
            List<AgentTool> allDynamicTools = pluginRegistry.getAllPluginTools();
            for (AgentTool existingTool : allDynamicTools) {
                for (Method method : existingTool.getClass().getMethods()) {
                    dev.langchain4j.agent.tool.Tool toolAnn = method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
                    if (toolAnn != null) {
                        String existingToolName = toolAnn.name();
                        if (existingToolName.isEmpty()) {
                            existingToolName = method.getName();
                        }
                        if (scanResult.toolNames.contains(existingToolName)) {
                            if (!conflictingTools.contains(existingToolName)) {
                                conflictingTools.add(existingToolName);
                            }
                        }
                    }
                }
            }
        }

        if (conflictingPlugins.isEmpty() && conflictingTools.isEmpty()) {
            return null;
        }

        return new PluginConflictInfo(conflictingPlugins, conflictingTools);
    }

    @Override
    public byte[] exportPlugin(String toolName, String toolVersion) {
        if (toolName == null || toolName.isEmpty()) {
            log.warn("exportPlugin: toolName is empty");
            return null;
        }
        if (toolVersion == null || toolVersion.isEmpty()) {
            log.warn("exportPlugin: toolVersion is empty");
            return null;
        }
        ToolSetInfo tsInfo = getAllPluginToolSets().stream()
                .filter(ts -> toolName.equals(ts.getName()) && toolVersion.equals(ts.getVersion()))
                .findFirst().orElse(null);
        if (tsInfo == null) {
            log.warn("exportPlugin: no ToolSetInfo found for tool: {} {}", toolName, toolVersion);
            return null;
        }
        String jarFileName = tsInfo.getJarFileName();
        try {
            Path jarPath = jarPluginLoader.getPluginDir().resolve(jarFileName);
            File jarFile = jarPath.toFile();
            if (!jarFile.exists()) {
                log.warn("exportPlugin: jar file not found: {}", jarPath);
                return null;
            }

            PluginRegistry.PluginEntry entry = pluginRegistry.getPluginByJarFileName(jarFileName);
            if (entry == null) {
                log.warn("exportPlugin: plugin not loaded: {}", jarFileName);
                return null;
            }

            long fileSize = jarFile.length();
            String sha256Hash = calculateSHA256(jarPath);

            PluginExportInfo exportInfo = new PluginExportInfo(tsInfo, fileSize, sha256Hash);
            byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(exportInfo);

            String baseName = jarFileName;
            if (baseName.toLowerCase().endsWith(".jar")) {
                baseName = baseName.substring(0, baseName.length() - 4);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                ZipEntry jsonEntry = new ZipEntry(baseName + ".json");
                zos.putNextEntry(jsonEntry);
                zos.write(jsonBytes);
                zos.closeEntry();

                ZipEntry jarEntry = new ZipEntry(jarFileName);
                zos.putNextEntry(jarEntry);
                Files.copy(jarPath, zos);
                zos.closeEntry();
            }

            log.info("exportPlugin: exported {} ({}, SHA256: {})", jarFileName,
                    formatFileSize(fileSize), sha256Hash);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("exportPlugin: error exporting plugin: {}", jarFileName, e);
            return null;
        }
    }

    @Override
    public PluginInstallResult installPluginFromBytes(byte[] zipBytes) throws Exception {
        PluginExportInfo exportInfo = null;
        byte[] jarBytes = null;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".json")) {
                    byte[] jsonBytes = zis.readAllBytes();
                    exportInfo = objectMapper.readValue(jsonBytes, PluginExportInfo.class);
                } else if (entry.getName().endsWith(".jar")) {
                    jarBytes = zis.readAllBytes();
                }
                zis.closeEntry();
            }
        }

        if (exportInfo == null) {
            throw new IllegalArgumentException("ZIP包中未找到插件描述文件");
        }
        if (jarBytes == null) {
            throw new IllegalArgumentException("ZIP包中未找到插件JAR文件");
        }

        String toolName = exportInfo.getName();
        String version = exportInfo.getVersion();
        String jarFileName = exportInfo.getJarFileName();

        if (jarFileName == null || !jarFileName.toLowerCase().endsWith(".jar")) {
            jarFileName = toolName + ".jar";
        }

        boolean isUpgrade = false;
        String previousVersion = null;

        Path targetPath = jarPluginLoader.getPluginDir().resolve(jarFileName);
        File existingFile = targetPath.toFile();

        if (existingFile.exists() || pluginRegistry.hasPluginJar(jarFileName)) {
            isUpgrade = true;
            PluginRegistry.PluginEntry existing = pluginRegistry.getPluginByJarFileName(jarFileName);
            if (existing != null) {
                previousVersion = existing.getPlugin().getVersion();
            }
            jarPluginLoader.unloadAndDeletePlugin(jarFileName);
        }

        Files.write(targetPath, jarBytes);
        log.info("Plugin JAR written to: {}", targetPath);

        PluginConflictInfo conflictInfo = detectConflicts(targetPath.toFile(), toolName);

        int toolCount = jarPluginLoader.loadPlugin(targetPath.toFile());

        return PluginInstallResult.success(toolName, version, jarFileName, toolCount, isUpgrade, previousVersion, conflictInfo);
    }

    @Override
    public PluginInstallResult installPluginFromJarFile(Path jarPath) throws Exception {
        return installPluginFromJarFile(jarPath, null);
    }

    @Override
    public PluginInstallResult installPluginFromJarFile(Path jarPath, String jarFileName) throws Exception {
        if (jarPath == null) {
            throw new IllegalArgumentException("jarPath 不能为空");
        }
        File src = jarPath.toFile();
        if (!src.exists() || !src.isFile()) {
            throw new IllegalArgumentException("JAR 文件不存在或不是文件: " + jarPath);
        }
        if (!src.canRead()) {
            throw new IllegalArgumentException("JAR 文件不可读: " + jarPath);
        }
        if (src.length() <= 0L) {
            throw new IllegalArgumentException("JAR 文件为空: " + jarPath);
        }

        // 目标文件名优先取调用方指定的原始文件名，否则回退用源文件的名字
        if (jarFileName == null || jarFileName.trim().isEmpty()) {
            jarFileName = src.getName();
        }
        jarFileName = jarFileName.trim();
        if (!jarFileName.toLowerCase().endsWith(".jar")) {
            throw new IllegalArgumentException("文件扩展名必须为 .jar: " + jarFileName);
        }

        // 先扫描 JAR 拿到插件标识/版本/工具名（用于冲突检测与升级判断）
        JarPluginLoader.PluginScanResult scanResult = jarPluginLoader.scanPluginInfo(src);
        if (scanResult.hasError() || scanResult.pluginId == null) {
            String err = scanResult.errorMessage != null ? scanResult.errorMessage : "JAR 内未发现可用的 AgentPlugin 实现";
            return PluginInstallResult.fail(jarFileName, null, jarFileName, "JAR 无效: " + err);
        }

        boolean isUpgrade = false;
        String previousVersion = null;
        Path targetPath = jarPluginLoader.getPluginDir().resolve(jarFileName);
        File existingFile = targetPath.toFile();
        if (existingFile.exists() || pluginRegistry.hasPluginJar(jarFileName)) {
            isUpgrade = true;
            PluginRegistry.PluginEntry existing = pluginRegistry.getPluginByJarFileName(jarFileName);
            if (existing != null) {
                previousVersion = existing.getPlugin().getVersion();
            }
            jarPluginLoader.unloadAndDeletePlugin(jarFileName);
        }

        Files.copy(src.toPath(), targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.info("Plugin JAR copied from {} to {}", src, targetPath);

        PluginConflictInfo conflictInfo = detectConflicts(targetPath.toFile(), scanResult.pluginId);

        int toolCount = jarPluginLoader.loadPlugin(targetPath.toFile());

        String version = scanResult.pluginVersion;
        try {
            // 加载后以注册表中的插件版本为准
            PluginRegistry.PluginEntry entry = pluginRegistry.getPluginByJarFileName(jarFileName);
            if (entry != null) {
                version = entry.getPlugin().getVersion();
            }
        } catch (Exception ignored) {
            // 版本读取失败不阻塞安装结果
        }

        return PluginInstallResult.success(scanResult.pluginId, version, jarFileName, toolCount, isUpgrade, previousVersion, conflictInfo);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
