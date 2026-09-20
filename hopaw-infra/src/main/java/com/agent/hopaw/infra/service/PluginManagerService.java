package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.PluginConflictInfo;
import com.agent.hopaw.infra.model.dto.PluginDescriptor;
import com.agent.hopaw.infra.model.dto.PluginInstallResult;
import com.agent.hopaw.infra.model.dto.PluginPackageManifest;
import com.agent.hopaw.infra.model.dto.PluginUpdateInfo;
import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.plugin.AgentPlugin;
import com.agent.hopaw.infra.plugin.JarPluginLoader;
import com.agent.hopaw.infra.plugin.PluginRegistry;
import com.agent.hopaw.infra.tool.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 插件管理（一级）服务实现：安装 / 升级 / 卸载 / 导出 / 启停 / 插件配置信息 / invoke。
 *
 * <p>插件下的工具集元数据由 {@link ToolSetService}（二级）负责，本类只处理「插件」这一层：
 * 以 pluginId 为标识，JAR 文件名仅为物理定位属性。</p>
 */
@Service
public class PluginManagerService implements IAgentPluginService {

    private static final Logger log = LoggerFactory.getLogger(PluginManagerService.class);
    private static final int BUFFER_SIZE = 8192;

    private final PluginRegistry pluginRegistry;
    private final JarPluginLoader jarPluginLoader;
    private final PluginStateService pluginStateService;
    private final ConfigItemStore configItemStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PluginManagerService(PluginRegistry pluginRegistry,
                                JarPluginLoader jarPluginLoader,
                                PluginStateService pluginStateService,
                                ConfigItemStore configItemStore) {
        this.pluginRegistry = pluginRegistry;
        this.jarPluginLoader = jarPluginLoader;
        this.pluginStateService = pluginStateService;
        this.configItemStore = configItemStore;
    }

    // ==================== 查询 ====================

    @Override
    public List<PluginDescriptor> getPlugins() {
        List<PluginDescriptor> result = new ArrayList<>();
        for (PluginRegistry.PluginEntry entry : pluginRegistry.getAllPluginEntries()) {
            result.add(toDescriptor(entry));
        }
        result.sort(Comparator.comparing(PluginDescriptor::getId));
        return result;
    }

    @Override
    public PluginDescriptor getPlugin(String pluginId) {
        PluginRegistry.PluginEntry entry = pluginRegistry.getPlugin(pluginId);
        return entry == null ? null : toDescriptor(entry);
    }

    private PluginDescriptor toDescriptor(PluginRegistry.PluginEntry entry) {
        AgentPlugin plugin = entry.getPlugin();
        PluginDescriptor descriptor = PluginDescriptor.of(plugin);
        List<AgentTool> tools = entry.getTools();
        descriptor.setToolSetNames(tools.stream().map(AgentTool::getName).toArray(String[]::new));
        descriptor.setToolCount(tools.size());
        descriptor.setFrontendAssetCount(entry.getAssets().size());
        descriptor.setInvokeSupported(supportsInvoke(plugin));
        descriptor.setEnabled(pluginStateService.isEnabled(entry.getPluginId()));
        descriptor.setJarFileName(entry.getJarFileName());
        return descriptor;
    }

    // ==================== 启停 ====================

    @Override
    public boolean isEnabled(String pluginId) {
        return pluginStateService.isEnabled(pluginId);
    }

    @Override
    public void setEnabled(String pluginId, boolean enabled) {
        if (pluginRegistry.getPlugin(pluginId) == null) {
            throw new IllegalArgumentException("插件不存在：" + pluginId);
        }
        pluginStateService.setEnabled(pluginId, enabled);
    }

    // ==================== 卸载 / 导出 ====================

    @Override
    public boolean unloadPlugin(String pluginId, boolean cleanConfig) {
        PluginRegistry.PluginEntry entry = pluginRegistry.getPlugin(pluginId);
        if (entry == null) {
            log.warn("unloadPlugin: plugin not found: {}", pluginId);
            return false;
        }
        // 卸载前先取出清理所需的配置定义（卸载后 classloader 关闭、实例不可再用）
        List<AgentTool> tools = new ArrayList<>(entry.getTools());
        List<ToolConfigItem> pluginConfigItems = entry.getPlugin().getConfigItems();

        boolean result = jarPluginLoader.unloadAndDeletePlugin(entry.getJarFileName());
        if (result) {
            if (cleanConfig) {
                // 插件级配置 + 该插件下所有工具集的工具级配置（含 MAP 散键）
                configItemStore.deleteAll(PluginConfigService.prefix(pluginId), pluginConfigItems);
                for (AgentTool tool : tools) {
                    configItemStore.deleteAll(tool.getConfigPrefix(), tool.getConfigItems());
                }
                log.info("Cleaned config of plugin [{}]: {} plugin-level items, {} tool sets",
                        pluginId, pluginConfigItems.size(), tools.size());
            }
            pluginStateService.remove(pluginId);
        }
        return result;
    }

    @Override
    public byte[] exportPlugin(String pluginId) {
        PluginRegistry.PluginEntry entry = pluginRegistry.getPlugin(pluginId);
        if (entry == null) {
            log.warn("exportPlugin: plugin not found: {}", pluginId);
            return null;
        }
        String jarFileName = entry.getJarFileName();
        try {
            Path jarPath = jarPluginLoader.getPluginDir().resolve(jarFileName);
            File jarFile = jarPath.toFile();
            if (!jarFile.exists()) {
                log.warn("exportPlugin: jar file not found: {}", jarPath);
                return null;
            }

            long fileSize = jarFile.length();
            String sha256Hash = calculateSHA256(jarPath);
            PluginPackageManifest manifest = buildManifest(entry, fileSize, sha256Hash);
            byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);

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

            log.info("exportPlugin: exported plugin [{}] ({}, SHA256: {})",
                    pluginId, formatFileSize(fileSize), sha256Hash);
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("exportPlugin: error exporting plugin [{}]", pluginId, e);
            return null;
        }
    }

    private PluginPackageManifest buildManifest(PluginRegistry.PluginEntry entry, long fileSize, String sha256Hash) {
        AgentPlugin plugin = entry.getPlugin();
        PluginPackageManifest manifest = new PluginPackageManifest();
        manifest.setId(plugin.getId());
        manifest.setName(plugin.getName());
        manifest.setDescription(plugin.getDescription());
        manifest.setVersion(plugin.getVersion());
        manifest.setAuthor(plugin.getAuthor());
        manifest.setUrl(plugin.getUrl());
        manifest.setIcon(plugin.getIcon());
        manifest.setKeyword(plugin.getKeyword());
        manifest.setJarFileName(entry.getJarFileName());
        manifest.setFileSize(fileSize);
        manifest.setSha256Hash(sha256Hash);

        List<PluginPackageManifest.ProvidedToolSet> provides = new ArrayList<>();
        for (AgentTool tool : entry.getTools()) {
            provides.add(new PluginPackageManifest.ProvidedToolSet(
                    tool.getName(), tool.getDescription(), countToolMethods(tool)));
        }
        manifest.setProvides(provides);
        manifest.setFrontendAssetCount(entry.getAssets().size());
        manifest.setInvokeSupport(supportsInvoke(plugin));
        manifest.setConfigItems(plugin.getConfigItems());
        return manifest;
    }

    /** 清单基础校验：只接受 v2 清单，且必须声明 id 与至少一项能力。 */
    private void validateManifest(PluginPackageManifest manifest) {
        if (manifest.getManifestVersion() != 2) {
            throw new IllegalArgumentException(String.format(
                    "不支持的插件清单版本：%d，只接受 v2 清单（manifestVersion=2）", manifest.getManifestVersion()));
        }
        if (manifest.getId() == null || manifest.getId().trim().isEmpty()) {
            throw new IllegalArgumentException("插件清单缺少 id（插件标识）");
        }
        if (!manifest.hasAnyCapability()) {
            throw new IllegalArgumentException(
                    "插件清单未声明任何能力：至少需要 provides 工具集、前端资产或 invoke 之一");
        }
    }

    /** 是否为「纯前端插件」：不提供任何工具集，仅靠前端资产 / invoke 生效。 */
    private boolean isFrontendOnly(PluginPackageManifest manifest) {
        return manifest.getProvides() == null || manifest.getProvides().isEmpty();
    }

    // ==================== 安装 / 升级 ====================

    @Override
    public PluginInstallResult installOrUpgradePlugin(PluginUpdateInfo updateInfo) {
        return installOrUpgradePlugin(updateInfo, null, null);
    }

    @Override
    public PluginInstallResult installOrUpgradePlugin(PluginUpdateInfo updateInfo,
                                                      Consumer<String> stageCallback,
                                                      Consumer<Integer> downloadProgressCallback) {
        String pluginId = updateInfo.getPluginId();
        String version = updateInfo.getVersion();
        String jarFileName = updateInfo.getFileName();

        if (jarFileName == null || !jarFileName.toLowerCase().endsWith(".jar")) {
            jarFileName = pluginId + ".jar";
        }

        if (updateInfo.getDownloadUrl() == null || updateInfo.getDownloadUrl().isEmpty()) {
            log.error("Download URL is empty for plugin: {}", pluginId);
            return PluginInstallResult.fail(pluginId, version, jarFileName, "下载地址为空");
        }

        if (updateInfo.getSha256Hash() == null || updateInfo.getSha256Hash().isEmpty()) {
            log.error("SHA256 hash is empty for plugin: {}", pluginId);
            return PluginInstallResult.fail(pluginId, version, jarFileName, "插件哈希值为空");
        }

        Path pluginDir = jarPluginLoader.getPluginDir();
        Path targetPath = pluginDir.resolve(jarFileName);
        String previousVersion = updateInfo.getCurrentVersion();
        boolean isUpgrade = updateInfo.isInstalled();

        try {
            if (isUpgrade) {
                log.info("Plugin {} is installed, uninstalling before upgrade", pluginId);
                reportStage(stageCallback, "uninstalling");
                // unregister 内部会统一调用各工具与插件的 destroy 并关闭 classloader，无需重复 destroy
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
                return PluginInstallResult.fail(pluginId, version, jarFileName,
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
            PluginPackageManifest downloadedManifest = null;
            try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(tempZip))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.getName().endsWith(".json")) {
                        downloadedManifest = objectMapper.readValue(zis.readAllBytes(), PluginPackageManifest.class);
                    } else if (entry.getName().endsWith(".jar")) {
                        jarBytes = zis.readAllBytes();
                    }
                    zis.closeEntry();
                }
            }

            if (downloadedManifest == null) {
                log.error("ZIP包中未找到插件清单文件（.json）");
                Files.deleteIfExists(tempZip);
                return PluginInstallResult.fail(pluginId, version, jarFileName, "ZIP包中未找到插件清单文件（.json）");
            }

            // 仓库/商店链路同样只接受 v2 清单，并在此拦截「纯前端插件」
            try {
                validateManifest(downloadedManifest);
            } catch (IllegalArgumentException e) {
                log.error("插件清单校验失败: {}", e.getMessage());
                Files.deleteIfExists(tempZip);
                return PluginInstallResult.fail(pluginId, version, jarFileName, e.getMessage());
            }

            if (isFrontendOnly(downloadedManifest) && !updateInfo.isAllowFrontendOnly()) {
                log.warn("拒绝安装未经确认的纯前端插件: {}", downloadedManifest.getId());
                Files.deleteIfExists(tempZip);
                return PluginInstallResult.fail(pluginId, version, jarFileName,
                        "该插件不提供任何工具集，属于「纯前端插件」——其前端资源会注入到聊天页面，"
                                + "请确认来源可信后重新发起安装（需显式确认）。");
            }

            if (jarBytes == null) {
                log.error("ZIP包中未找到插件JAR文件");
                Files.deleteIfExists(tempZip);
                return PluginInstallResult.fail(pluginId, version, jarFileName, "ZIP包中未找到插件JAR文件");
            }

            reportStage(stageCallback, "verifying");
            String downloadedJarHash = calculateSHA256(jarBytes);
            if (!updateInfo.getSha256Hash().equalsIgnoreCase(downloadedJarHash)) {
                log.error("JAR文件SHA256哈希校验失败！期望: {}, 实际: {}",
                        updateInfo.getSha256Hash(), downloadedJarHash);
                Files.deleteIfExists(tempZip);
                return PluginInstallResult.fail(pluginId, version, jarFileName, "插件哈希校验失败");
            }

            reportStage(stageCallback, "installing");
            Files.write(targetPath, jarBytes);
            log.info("Plugin {} installed successfully to {}, JAR哈希校验通过", pluginId, targetPath);

            PluginConflictInfo conflictInfo = detectConflicts(targetPath.toFile());

            int toolCount = jarPluginLoader.loadPlugin(targetPath.toFile());

            Files.deleteIfExists(tempZip);

            return PluginInstallResult.success(pluginId, version, jarFileName, toolCount, isUpgrade, previousVersion, conflictInfo);
        } catch (Exception e) {
            log.error("Error installing/upgrading plugin: {}", pluginId, e);
            return PluginInstallResult.fail(pluginId, version, jarFileName,
                    String.format("安装失败: %s", e.getMessage()));
        }
    }

    @Override
    public PluginInstallResult installPluginFromBytes(byte[] zipBytes) throws Exception {
        PluginPackageManifest manifest = null;
        byte[] jarBytes = null;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith(".json")) {
                    byte[] jsonBytes = zis.readAllBytes();
                    manifest = objectMapper.readValue(jsonBytes, PluginPackageManifest.class);
                } else if (entry.getName().endsWith(".jar")) {
                    jarBytes = zis.readAllBytes();
                }
                zis.closeEntry();
            }
        }

        if (manifest == null) {
            throw new IllegalArgumentException("ZIP包中未找到插件清单文件（.json）");
        }
        validateManifest(manifest);
        if (jarBytes == null) {
            throw new IllegalArgumentException("ZIP包中未找到插件JAR文件");
        }

        String pluginId = manifest.getId();
        String version = manifest.getVersion();
        String jarFileName = manifest.getJarFileName();
        if (jarFileName == null || !jarFileName.toLowerCase().endsWith(".jar")) {
            jarFileName = pluginId + ".jar";
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

        PluginConflictInfo conflictInfo = detectConflicts(targetPath.toFile());

        int toolCount = jarPluginLoader.loadPlugin(targetPath.toFile());

        return PluginInstallResult.success(pluginId, version, jarFileName, toolCount, isUpgrade, previousVersion, conflictInfo);
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

        // 先扫描 JAR 拿到插件标识/版本（用于冲突检测与升级判断）
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

        PluginConflictInfo conflictInfo = detectConflicts(targetPath.toFile());

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

    // ==================== 配置信息 / invoke ====================

    @Override
    public Map<String, Object> getPluginConfigInfo(String pluginId) {
        PluginRegistry.PluginEntry entry = pluginRegistry.getPlugin(pluginId);
        if (entry == null) {
            return Map.of("hasConfig", false);
        }
        List<String> configKeys = new ArrayList<>();
        for (ToolConfigItem item : entry.getPlugin().getConfigItems()) {
            configKeys.add(PluginConfigService.prefix(pluginId) + item.getKey());
        }
        List<String> toolSetsWithConfig = new ArrayList<>();
        for (AgentTool tool : entry.getTools()) {
            if (!tool.getConfigItems().isEmpty()) {
                toolSetsWithConfig.add(tool.getName());
                for (ToolConfigItem item : tool.getConfigItems()) {
                    configKeys.add(tool.getConfigPrefix() + item.getKey());
                }
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("hasConfig", !configKeys.isEmpty());
        result.put("configKeys", configKeys);
        result.put("toolSetCount", entry.getTools().size());
        result.put("toolSetsWithConfig", toolSetsWithConfig);
        return result;
    }

    @Override
    public Map<String, Object> invoke(String pluginId, String toolRef, Map<String, Object> params) {
        PluginRegistry.PluginEntry entry = pluginRegistry.getPlugin(pluginId);
        if (entry == null) {
            throw new IllegalArgumentException("插件不存在：" + pluginId);
        }
        if (!pluginStateService.isEnabled(pluginId)) {
            throw new IllegalArgumentException("插件已禁用：" + pluginId);
        }
        return entry.getPlugin().invoke(toolRef, params == null ? Map.of() : params);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 冲突检测：同 pluginId 被不同 JAR 占用（插件冲突），或工具方法名与其他插件工具重名（工具冲突）。
     */
    private PluginConflictInfo detectConflicts(File jarFile) {
        List<String> conflictingPlugins = new ArrayList<>();
        List<String> conflictingTools = new ArrayList<>();

        JarPluginLoader.PluginScanResult scanResult = jarPluginLoader.scanPluginInfo(jarFile);
        if (scanResult.hasError() || scanResult.pluginId == null) {
            return null;
        }

        for (PluginRegistry.PluginEntry entry : pluginRegistry.getPlugins().values()) {
            if (!entry.getJarFileName().equals(jarFile.getName())
                    && scanResult.pluginId.equals(entry.getPluginId())) {
                conflictingPlugins.add(entry.getPluginId());
            }
        }

        if (scanResult.toolNames != null) {
            for (AgentTool existingTool : pluginRegistry.getAllPluginTools()) {
                for (Method method : existingTool.getClass().getMethods()) {
                    dev.langchain4j.agent.tool.Tool toolAnn = method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
                    if (toolAnn != null) {
                        String existingToolName = toolAnn.name();
                        if (existingToolName.isEmpty()) {
                            existingToolName = method.getName();
                        }
                        if (scanResult.toolNames.contains(existingToolName)
                                && !conflictingTools.contains(existingToolName)) {
                            conflictingTools.add(existingToolName);
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

    private int countToolMethods(AgentTool tool) {
        int count = 0;
        for (Method method : tool.getClass().getMethods()) {
            if (method.getAnnotation(dev.langchain4j.agent.tool.Tool.class) != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 插件是否覆写了 {@code invoke(toolRef, params)}（未覆写即不支持 invoke）。
     */
    private boolean supportsInvoke(AgentPlugin plugin) {
        try {
            Method method = plugin.getClass().getMethod("invoke", String.class, Map.class);
            return method.getDeclaringClass() != AgentPlugin.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private void reportStage(Consumer<String> callback, String stage) {
        if (callback != null) {
            callback.accept(stage);
        }
    }

    private void reportProgress(Consumer<Integer> callback, int percent) {
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
        return toHex(digest.digest());
    }

    private String calculateSHA256(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return toHex(digest.digest(data));
    }

    private String toHex(byte[] hashBytes) {
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

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /** 保留：清单里 provides 顺序稳定，便于对比查看。 */
    @SuppressWarnings("unused")
    private Map<String, Object> describeProvides(PluginRegistry.PluginEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (AgentTool tool : entry.getTools()) {
            map.put(tool.getName(), countToolMethods(tool));
        }
        return map;
    }
}
