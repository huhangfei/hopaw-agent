package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.PluginConflictInfo;
import com.agent.hopaw.infra.model.dto.PluginDescriptor;
import com.agent.hopaw.infra.model.dto.PluginInstallResult;
import com.agent.hopaw.infra.model.dto.PluginPackageManifest;
import com.agent.hopaw.infra.model.dto.PluginUpdateInfo;
import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.plugin.AgentPlugin;
import com.agent.hopaw.infra.plugin.JarPluginLoader;
import com.agent.hopaw.infra.plugin.PluginAsset;
import com.agent.hopaw.infra.plugin.PluginIconResolver;
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
    private final ToolStateService toolStateService;
    private final ConfigItemStore configItemStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PluginManagerService(PluginRegistry pluginRegistry,
                                JarPluginLoader jarPluginLoader,
                                PluginStateService pluginStateService,
                                ToolStateService toolStateService,
                                ConfigItemStore configItemStore) {
        this.pluginRegistry = pluginRegistry;
        this.jarPluginLoader = jarPluginLoader;
        this.pluginStateService = pluginStateService;
        this.toolStateService = toolStateService;
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
        // 插件图标从 JAR 内联出来下发（plugin.getIcon() 只是 JAR 内文件名，浏览器无法直接请求）
        descriptor.setIcon(PluginIconResolver.resolvePluginIcon(entry, plugin.getIcon()));
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
        // 卸载前先取出清理所需的配置定义与前缀：卸载会关闭 classloader，
        // 之后再去调插件类实例上的 getConfigPrefix()/getConfigItems() 不再可靠
        List<ToolConfigItem> pluginConfigItems = List.copyOf(entry.getPlugin().getConfigItems());
        List<ToolConfigCleanup> toolCleanups = new ArrayList<>();
        List<String> toolSetNames = new ArrayList<>();
        for (AgentTool tool : entry.getTools()) {
            toolCleanups.add(new ToolConfigCleanup(tool.getConfigPrefix(), List.copyOf(tool.getConfigItems())));
            toolSetNames.add(tool.getName());
        }

        boolean result = jarPluginLoader.unloadAndDeletePlugin(entry.getJarFileName());
        if (result) {
            if (cleanConfig) {
                // 插件级配置（含 MAP 散键）
                configItemStore.deleteAll(PluginConfigService.prefix(pluginId), pluginConfigItems);
                // 各工具集配置：键已挂在 plugin.<id>.tool.<工具集名>. 下，逐个按声明项清理
                for (ToolConfigCleanup cleanup : toolCleanups) {
                    configItemStore.deleteAll(cleanup.prefix(), cleanup.configItems());
                }
                // 兜底：插件根前缀扫一遍，覆盖已从代码中移除的历史配置项，保证 plugin.<id>.* 无残留
                int swept = configItemStore.deleteByPrefix(PluginConfigService.prefix(pluginId));
                log.info("Cleaned config of plugin [{}]: {} plugin-level items, {} tool sets, {} keys swept by prefix",
                        pluginId, pluginConfigItems.size(), toolCleanups.size(), swept);
            }
            // 清理该插件下所有工具集的工具级/方法级禁用状态
            for (String toolSetName : toolSetNames) {
                toolStateService.removeToolSet(toolSetName);
            }
            pluginStateService.remove(pluginId);
        }
        return result;
    }

    /** 卸载清理留痕：前缀 + 配置项定义，避免卸载后依赖插件类实例计算。 */
    private record ToolConfigCleanup(String prefix, List<ToolConfigItem> configItems) {
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
                    tool.getName(), tool.getDescription(), countToolMethods(tool), collectToolMethods(tool)));
        }
        manifest.setProvides(provides);
        manifest.setFrontendAssetCount(entry.getAssets().size());
        manifest.setFrontendAssets(collectFrontendAssets(entry));
        manifest.setInvokeSupport(supportsInvoke(plugin));
        manifest.setConfigItems(plugin.getConfigItems());
        return manifest;
    }

    /** 收集工具集的方法明细：@Tool 注解声明的名称与描述（按名称排序，保证导出稳定）。 */
    private List<PluginPackageManifest.ProvidedToolMethod> collectToolMethods(AgentTool tool) {
        List<PluginPackageManifest.ProvidedToolMethod> methods = new ArrayList<>();
        for (Method method : tool.getClass().getMethods()) {
            dev.langchain4j.agent.tool.Tool ann =
                    method.getAnnotation(dev.langchain4j.agent.tool.Tool.class);
            if (ann == null) {
                continue;
            }
            String name = (ann.name() == null || ann.name().isEmpty()) ? method.getName() : ann.name();
            String description = (ann.value() == null) ? "" : String.join("；", ann.value());
            methods.add(new PluginPackageManifest.ProvidedToolMethod(name, description));
        }
        methods.sort(Comparator.comparing(PluginPackageManifest.ProvidedToolMethod::getName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return methods;
    }

    /** 收集前端资产明细：从插件 JAR 条目读取未压缩大小，读取失败时大小记为 -1（前端显示「未知」）。 */
    private List<PluginPackageManifest.ProvidedAsset> collectFrontendAssets(PluginRegistry.PluginEntry entry) {
        List<PluginAsset> assets = entry.getAssets();
        List<PluginPackageManifest.ProvidedAsset> result = new ArrayList<>();
        if (assets == null || assets.isEmpty()) {
            return result;
        }
        Path jarPath = jarPluginLoader.getPluginDir().resolve(entry.getJarFileName());
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath.toFile())) {
            for (PluginAsset asset : assets) {
                long size = -1;
                java.util.jar.JarEntry jarEntry = jar.getJarEntry(asset.getPath());
                if (jarEntry != null) {
                    size = jarEntry.getSize();
                }
                result.add(toProvidedAsset(asset, size));
            }
        } catch (Exception e) {
            log.warn("Failed to read asset sizes from {}: {}", entry.getJarFileName(), e.getMessage());
            for (PluginAsset asset : assets) {
                result.add(toProvidedAsset(asset, -1));
            }
        }
        return result;
    }

    private PluginPackageManifest.ProvidedAsset toProvidedAsset(PluginAsset asset, long size) {
        String path = asset.getPath() == null ? "" : asset.getPath();
        String name = path.substring(path.lastIndexOf('/') + 1);
        return new PluginPackageManifest.ProvidedAsset(name, path, asset.getType(), size);
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
        // 升级判定以注册表为准：插件新版常常改了 JAR 文件名（Maven 产物名带版本号），
        // 只认 updateInfo.installed / 同名文件会漏掉旧条目，新包随后会被「pluginId 已被占用」直接拒掉
        PluginRegistry.PluginEntry installedEntry = pluginRegistry.getPlugin(pluginId);
        boolean isUpgrade = updateInfo.isInstalled() || installedEntry != null;
        String previousVersion = installedEntry != null
                ? installedEntry.getPlugin().getVersion() : updateInfo.getCurrentVersion();
        boolean sameJarEntry = pluginRegistry.hasPluginJar(jarFileName);
        String staleJarName = null;

        try {
            if (isUpgrade || sameJarEntry || targetPath.toFile().exists()) {
                log.info("Plugin {} is installed, uninstalling before upgrade", pluginId);
                reportStage(stageCallback, "uninstalling");
                // unregister 内部会统一调用各工具与插件的 destroy 并关闭 classloader，无需重复 destroy
                if (installedEntry != null) {
                    // 自己的旧版本一律拆掉：旧 JAR 名与本次目标不同时，旧文件登记为待清理
                    staleJarName = detachPluginForReplace(pluginId, jarFileName);
                } else if (sameJarEntry) {
                    // 注册表占着同名 JAR 但 pluginId 对不上：兜底按文件名注销（文件随后被覆盖写入）
                    jarPluginLoader.unloadPluginKeepJar(jarFileName);
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
            // 新包已落盘，清掉升级前的旧 JAR 残留，避免下次启动又扫出一份同标识插件
            deleteStaleJar(staleJarName, targetPath);
            log.info("Plugin {} installed successfully to {}, JAR哈希校验通过", pluginId, targetPath);

            String effectivePluginId = downloadedManifest.getId() != null
                    ? downloadedManifest.getId() : pluginId;
            PluginConflictInfo conflictInfo = detectConflicts(targetPath.toFile(), effectivePluginId);

            int toolCount = jarPluginLoader.loadPlugin(targetPath.toFile());

            Files.deleteIfExists(tempZip);

            if (!pluginRegistry.hasPlugin(effectivePluginId)) {
                log.error("Plugin [{}] 已落盘但未被加载，安装未生效", effectivePluginId);
                return PluginInstallResult.fail(pluginId, version, jarFileName,
                        "插件包已写入 " + jarFileName + "，但加载被拒绝（JAR 无效或注册表中存在同标识插件），"
                                + "请检查 plugins 目录后重试");
            }

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
        String staleJarName = null;

        Path targetPath = jarPluginLoader.getPluginDir().resolve(jarFileName);
        File existingFile = targetPath.toFile();

        // 以 pluginId 为升级判定依据（新包可能换了 JAR 文件名），把「自己的旧版本」排除在重复之外
        PluginRegistry.PluginEntry installedEntry = pluginRegistry.getPlugin(pluginId);
        if (installedEntry != null || existingFile.exists() || pluginRegistry.hasPluginJar(jarFileName)) {
            isUpgrade = true;
            if (installedEntry != null) {
                previousVersion = installedEntry.getPlugin().getVersion();
                staleJarName = detachPluginForReplace(pluginId, jarFileName);
            } else if (pluginRegistry.hasPluginJar(jarFileName)) {
                jarPluginLoader.unloadPluginKeepJar(jarFileName);
            }
        }

        Files.write(targetPath, jarBytes);
        deleteStaleJar(staleJarName, targetPath);
        log.info("Plugin JAR written to: {}", targetPath);

        PluginConflictInfo conflictInfo = detectConflicts(targetPath.toFile(), pluginId);

        int toolCount = jarPluginLoader.loadPlugin(targetPath.toFile());

        if (!pluginRegistry.hasPlugin(pluginId)) {
            log.error("Plugin [{}] 已落盘但未被加载，安装未生效", pluginId);
            return PluginInstallResult.fail(pluginId, version, jarFileName,
                    "插件包已写入 " + jarFileName + "，但加载被拒绝（JAR 无效或注册表中存在同标识插件），"
                            + "请检查 plugins 目录后重试");
        }

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
        String staleJarName = null;
        Path targetPath = jarPluginLoader.getPluginDir().resolve(jarFileName);
        File existingFile = targetPath.toFile();
        // 源文件即插件目录内目标文件（如通过 plugin_install 指定 plugins/ 下已有的 JAR）：
        // 此时绝不能先删目标文件——那等于删掉安装源，后续复制必然失败
        boolean sameFile = isSameFile(src.toPath(), targetPath);
        // 以 pluginId 判定升级：新包可能换了 JAR 文件名（带版本号），同名判定会漏掉「自己的旧版本」
        PluginRegistry.PluginEntry installedEntry = pluginRegistry.getPlugin(scanResult.pluginId);
        if (installedEntry != null || existingFile.exists() || pluginRegistry.hasPluginJar(jarFileName)) {
            isUpgrade = true;
            if (installedEntry != null) {
                previousVersion = installedEntry.getPlugin().getVersion();
                staleJarName = detachPluginForReplace(scanResult.pluginId, jarFileName);
            } else if (pluginRegistry.hasPluginJar(jarFileName)) {
                // 只注销插件释放 JAR 句柄，文件本体随后由覆盖写入 / 原子改名处理
                jarPluginLoader.unloadPluginKeepJar(jarFileName);
            }
        }

        if (sameFile) {
            // 源即目标：文件已在位，跳过复制
            log.info("Plugin JAR is already the target file, skip copy: {}", targetPath);
        } else {
            // 先落同目录临时文件再原子改名：避免直接 REPLACE_EXISTING 覆盖时
            // 被并发读取到半写文件，也规避源/目标为同一文件的边界情况
            Path tempTarget = Files.createTempFile(jarPluginLoader.getPluginDir(), "install-", ".tmp");
            try {
                Files.copy(src.toPath(), tempTarget, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                try {
                    Files.move(tempTarget, targetPath,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(tempTarget, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(tempTarget);
            }
            log.info("Plugin JAR copied from {} to {}", src, targetPath);
        }

        deleteStaleJar(staleJarName, targetPath, src.toPath());

        PluginConflictInfo conflictInfo = detectConflicts(targetPath.toFile(), scanResult.pluginId);

        int toolCount = jarPluginLoader.loadPlugin(targetPath.toFile());

        if (!pluginRegistry.hasPlugin(scanResult.pluginId)) {
            log.error("Plugin [{}] 已落盘但未被加载，安装未生效", scanResult.pluginId);
            return PluginInstallResult.fail(scanResult.pluginId, scanResult.pluginVersion, jarFileName,
                    "插件包已写入 " + jarFileName + "，但加载被拒绝（JAR 无效或注册表中存在同标识插件），"
                            + "请检查 plugins 目录后重试");
        }

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
     *
     * <p><b>自己的旧版本不算冲突</b>：升级时新旧包的 JAR 文件名常常不同（Maven 产物名带版本号），
     * 同 pluginId 的旧条目正是本次要替换的对象，必须排除，否则每次升级都会报一堆"冲突"。</p>
     *
     * @param installingPluginId 本次安装的 pluginId（用于排除自身旧版本），可为 null
     */
    private PluginConflictInfo detectConflicts(File jarFile, String installingPluginId) {
        List<String> conflictingPlugins = new ArrayList<>();
        List<String> conflictingTools = new ArrayList<>();

        JarPluginLoader.PluginScanResult scanResult = jarPluginLoader.scanPluginInfo(jarFile);
        if (scanResult.hasError() || scanResult.pluginId == null) {
            return null;
        }

        for (PluginRegistry.PluginEntry entry : pluginRegistry.getPlugins().values()) {
            if (isSelfEntry(entry, jarFile, installingPluginId)) {
                continue;
            }
            if (scanResult.pluginId.equals(entry.getPluginId())) {
                conflictingPlugins.add(entry.getPluginId());
            }
            if (scanResult.toolNames == null) {
                continue;
            }
            for (AgentTool existingTool : entry.getTools()) {
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

    /** 条目是否为「本次安装要替换的自己」：同一 pluginId，或同一 JAR 文件（自己旧版本的两种表现形式）。 */
    private boolean isSelfEntry(PluginRegistry.PluginEntry entry, File jarFile, String installingPluginId) {
        if (jarFile.getName().equals(entry.getJarFileName())) {
            return true;
        }
        return installingPluginId != null && installingPluginId.equals(entry.getPluginId());
    }

    /**
     * 升级前拆掉同 pluginId 的旧注册条目（销毁工具/插件、释放 JAR 句柄）。
     *
     * <p>新旧包 JAR 文件名可能不同（例如 Maven 产物名带版本号）：只按文件名卸载会漏掉旧条目，
     * 之后 {@code loadPlugin} 会以「pluginId 已被占用」直接拒绝，表现为"更新成功但没生效"；
     * 旧文件残留还会在下次启动时再被扫出一份同标识插件。</p>
     *
     * @param targetJarName 本次要落盘的 JAR 文件名（同名即保留文件本体，由写入覆盖）
     * @return 需要在新包落盘后删除的旧 JAR 文件名；同名或无需删除时返回 null
     */
    private String detachPluginForReplace(String pluginId, String targetJarName) {
        PluginRegistry.PluginEntry existing = pluginRegistry.getPlugin(pluginId);
        if (existing == null) {
            return null;
        }
        String oldJarName = existing.getJarFileName();
        if (oldJarName == null) {
            // 理论上不会出现（注册时必带 JAR 名），兜底直接按 pluginId 注销
            pluginRegistry.unregister(pluginId);
            return null;
        }
        // 只注销、保留文件本体：同名文件随后会被覆盖写入，异名文件由调用方决定何时删除
        jarPluginLoader.unloadPluginKeepJar(oldJarName);
        if (oldJarName.equals(targetJarName)) {
            return null;
        }
        log.info("Replacing plugin [{}]: 旧 JAR [{}] 将在新包落盘后清理", pluginId, oldJarName);
        return oldJarName;
    }

    /**
     * 删除升级遗留的旧 JAR。keepPaths 内的文件（本次目标文件、安装源文件）不删，避免删掉刚装好的包或安装源。
     */
    private void deleteStaleJar(String staleJarName, Path... keepPaths) {
        if (staleJarName == null || staleJarName.isBlank()) {
            return;
        }
        Path pluginDir = jarPluginLoader.getPluginDir();
        Path stale = pluginDir.resolve(staleJarName).normalize();
        if (!stale.startsWith(pluginDir)) {
            log.error("Rejected stale jar path traversal: {}", staleJarName);
            return;
        }
        if (keepPaths != null) {
            for (Path keep : keepPaths) {
                if (keep != null && isSameFile(keep, stale)) {
                    return;
                }
            }
        }
        try {
            if (Files.deleteIfExists(stale)) {
                log.info("Deleted stale plugin JAR after upgrade: {}", stale);
            }
        } catch (Exception e) {
            log.warn("Failed to delete stale plugin JAR {}: {}", stale, e.getMessage());
        }
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

    /** 判断两个路径是否指向同一文件：先比规范化绝对路径，再用文件系统同一性判断兜底。 */
    private boolean isSameFile(Path a, Path b) {
        Path na = a.toAbsolutePath().normalize();
        Path nb = b.toAbsolutePath().normalize();
        if (na.equals(nb)) {
            return true;
        }
        try {
            return Files.isSameFile(na, nb);
        } catch (Exception e) {
            // 任一文件不存在时 isSameFile 抛异常，按不同文件处理
            return false;
        }
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
