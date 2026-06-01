package com.agent.hopaw.infra.tool;

import com.agent.hopaw.infra.constant.AgentToolSourceEnum;
import com.agent.hopaw.infra.event.ConfigChangeEvent;
import com.agent.hopaw.infra.model.dto.PluginExportInfo;
import com.agent.hopaw.infra.model.dto.PluginInstallResult;
import com.agent.hopaw.infra.model.dto.PluginUpdateInfo;
import com.agent.hopaw.infra.model.dto.PluginConflictInfo;
import com.agent.hopaw.infra.model.dto.ToolInfo;
import com.agent.hopaw.infra.model.dto.ToolParamInfo;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.plugin.DynamicToolRegistry;
import com.agent.hopaw.infra.plugin.JarPluginLoader;
import com.fasterxml.jackson.databind.JsonNode;
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
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Service
public class AgentToolService implements IAgentToolService {

    private static final Logger log = LoggerFactory.getLogger(AgentToolService.class);
    private static final int BUFFER_SIZE = 8192;

    private final ApplicationContext applicationContext;
    private final DynamicToolRegistry dynamicToolRegistry;
    private final JarPluginLoader jarPluginLoader;
    private final ObjectMapper objectMapper;

    public AgentToolService(ApplicationContext applicationContext, DynamicToolRegistry dynamicToolRegistry, JarPluginLoader jarPluginLoader) {
        this.applicationContext = applicationContext;
        this.dynamicToolRegistry = dynamicToolRegistry;
        this.jarPluginLoader = jarPluginLoader;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<AgentTool> getAgentTools() {
        Map<String, AgentTool> beans = applicationContext.getBeansOfType(AgentTool.class);
        List<AgentTool> tools = new ArrayList<>(beans.values());
        tools.addAll(dynamicToolRegistry.getAllDynamicTools());
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
            result.add(scanToolSet(agentTool, AgentToolSourceEnum.BUILT_IN));
        }
        result.addAll(getAllPluginToolSets());
        return result;
    }
    private List<ToolSetInfo> getAllPluginToolSets() {
        List<ToolSetInfo> result = new ArrayList<>();
        List<DynamicToolRegistry.PluginEntry> allPluginEntries = dynamicToolRegistry.getAllPluginEntries();
        for (DynamicToolRegistry.PluginEntry entry : allPluginEntries) {
            List<AgentTool> tools = entry.tools;
            for (AgentTool tool : tools) {
                ToolSetInfo toolSetInfo = scanToolSet(tool, AgentToolSourceEnum.PLUGIN);
                toolSetInfo.setJarFileName(entry.jarFileName);
                if(!AgentTool.DEFAULT_ICON.equals(toolSetInfo.getIcon())){
                    toolSetInfo.setIcon(entry.getCachedResource("static/icons/tools/"+tool.getIcon()));
                }
                result.add(toolSetInfo);
            }
        }
        return result;
    }
    private ToolSetInfo scanToolSet(AgentTool agentTool, AgentToolSourceEnum source) {
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
            ToolSecurityLevel methodSecurityAnn = method.getAnnotation(ToolSecurityLevel.class);
            if (methodSecurityAnn != null) {
                toolInfo.setSecurityLevel(methodSecurityAnn.value());
            }
            tools.add(toolInfo);
        }
        ToolSetInfo toolSetInfo = new ToolSetInfo(agentTool.getName(), agentTool.getDescription(), agentTool.getIcon(), tools, source);
        toolSetInfo.setVersion(agentTool.getVersion());
        toolSetInfo.setAuthor(agentTool.getAuthor());
        toolSetInfo.setUrl(agentTool.getUrl());
        toolSetInfo.setKeyword(agentTool.getKeyword());
        toolSetInfo.setHasConfigItems(!agentTool.getConfigItems().isEmpty());
        toolSetInfo.setAgentTool(agentTool);

        return toolSetInfo;
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
                DynamicToolRegistry.PluginEntry removed = dynamicToolRegistry.unregister(jarFileName);
                if (removed != null) {
                    for (AgentTool tool : removed.tools) {
                        try {
                            tool.destroy();
                        } catch (Exception e) {
                            log.error("Error calling destroy() on tool: {}", tool.getClass().getSimpleName(), e);
                        }
                    }
                }
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

    private PluginConflictInfo detectConflicts(File jarFile, String currentToolName) {
        List<String> conflictingPlugins = new ArrayList<>();
        List<String> conflictingTools = new ArrayList<>();

        JarPluginLoader.PluginScanResult scanResult = jarPluginLoader.scanPluginInfo(jarFile);
        if (scanResult.hasError() || scanResult.pluginName == null) {
            return null;
        }

        Map<String, DynamicToolRegistry.PluginEntry> allPlugins = dynamicToolRegistry.getPlugins();
        for (DynamicToolRegistry.PluginEntry entry : allPlugins.values()) {
            if (entry.tools.isEmpty()) continue;
            AgentTool existingTool = entry.tools.get(0);
            String existingPluginName = existingTool.getName();
            if (!entry.jarFileName.equals(jarFile.getName()) && existingPluginName.equals(scanResult.pluginName)) {
                conflictingPlugins.add(existingPluginName);
            }
        }

        if (scanResult.toolNames != null) {
            List<AgentTool> allDynamicTools = dynamicToolRegistry.getAllDynamicTools();
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

            DynamicToolRegistry.PluginEntry entry = dynamicToolRegistry.getPlugins().get(jarFileName);
            if (entry == null || entry.tools.isEmpty()) {
                log.warn("exportPlugin: plugin not loaded or has no tools: {}", jarFileName);
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

        if (existingFile.exists() || dynamicToolRegistry.hasPlugin(jarFileName)) {
            isUpgrade = true;
            DynamicToolRegistry.PluginEntry existing = dynamicToolRegistry.getPlugins().get(jarFileName);
            if (existing != null && !existing.tools.isEmpty()) {
                previousVersion = existing.tools.get(0).getVersion();
            }
            jarPluginLoader.unloadAndDeletePlugin(jarFileName);
        }

        Files.write(targetPath, jarBytes);
        log.info("Plugin JAR written to: {}", targetPath);

        PluginConflictInfo conflictInfo = detectConflicts(targetPath.toFile(), toolName);

        int toolCount = jarPluginLoader.loadPlugin(targetPath.toFile());

        return PluginInstallResult.success(toolName, version, jarFileName, toolCount, isUpgrade, previousVersion, conflictInfo);
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}
