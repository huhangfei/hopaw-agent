package com.agent.hopaw.pluginrepo.service;

import com.agent.hopaw.infra.model.dto.PluginPackageManifest;
import com.agent.hopaw.infra.model.dto.PluginRepoResult;
import com.agent.hopaw.infra.util.SemVer;
import com.agent.hopaw.pluginrepo.config.PluginRepoProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 插件仓库存储服务：管理 {@code packages/<pluginId>/<version>/} 目录下的插件包。
 *
 * <p><b>只认 v2 清单</b>（{@link PluginPackageManifest}）：不识别、也不包装任何 v1 格式。
 * 目录名即清单 {@code id}（pluginId），条目 name 不再承载"首个工具集名"的历史语义。</p>
 */
@Service
public class PluginRepoService {

    private static final Logger log = LoggerFactory.getLogger(PluginRepoService.class);

    /** 仓库唯一接受的清单版本 */
    private static final int MANIFEST_VERSION = 2;

    private final PluginRepoProperties properties;
    private final ObjectMapper objectMapper;
    private Path packagesDir;

    public PluginRepoService(PluginRepoProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() throws IOException {
        packagesDir = Paths.get(properties.getPackagesDir()).toAbsolutePath();
        Files.createDirectories(packagesDir);
        log.info("PluginRepo packages dir: {}", packagesDir);
    }

    private String getBaseUrl() {
        if (properties.getBaseUrl() != null && !properties.getBaseUrl().isEmpty()) {
            return properties.getBaseUrl();
        }
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest request = attrs.getRequest();
            String scheme = request.getScheme();
            int port = request.getServerPort();
            String host = request.getServerName();
            if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
                port = -1;
            }
            String base = scheme + "://" + host;
            if (port != -1) {
                base += ":" + port;
            }
            return base;
        }
        return null;
    }

    private String getQueryString() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            String query = attrs.getRequest().getQueryString();
            if (query != null && !query.isEmpty()) {
                return "?" + query;
            }
        }
        return "";
    }

    /** 构建某插件某版本的下载地址（含当前请求的 query，便于透传 api_key）。 */
    private String buildDownloadUrl(String pluginId, String version) {
        String base = getBaseUrl();
        String relPath = "/plugin-repo/api/download/" + urlEncode(pluginId) + "/" + urlEncode(version);
        return (base != null ? base : "") + relPath + getQueryString();
    }

    /**
     * 扫描仓库，返回**以插件为主体**的条目列表：一个条目 = 插件基本信息 + N 个版本（版本语义化降序）。
     * 非 v2 清单 / 缺 id / 无任何能力的目录会被跳过并记录日志。
     */
    public List<PluginRepoResult> scanPlugins() throws IOException {
        if (!Files.exists(packagesDir)) {
            return Collections.emptyList();
        }

        Map<String, PluginRepoResult> pluginMap = new LinkedHashMap<>();

        try (DirectoryStream<Path> pluginDirs = Files.newDirectoryStream(packagesDir)) {
            for (Path pluginDir : pluginDirs) {
                if (!Files.isDirectory(pluginDir)) {
                    continue;
                }
                String dirId = pluginDir.getFileName().toString();

                List<PluginPackageManifest> manifests = new ArrayList<>();
                try (DirectoryStream<Path> versionDirs = Files.newDirectoryStream(pluginDir)) {
                    for (Path versionDir : versionDirs) {
                        if (!Files.isDirectory(versionDir)) {
                            continue;
                        }
                        PluginPackageManifest manifest = readManifest(versionDir, dirId);
                        if (manifest == null) {
                            continue;
                        }
                        if (!dirId.equals(manifest.getId())) {
                            log.warn("插件目录名与清单 id 不一致，以清单为准：dir={}, id={}", dirId, manifest.getId());
                        }
                        manifests.add(manifest);
                    }
                }

                if (manifests.isEmpty()) {
                    continue;
                }

                // 版本语义化降序（不能按字符串排，否则 1.10.0 会排到 1.9.0 前面去）
                manifests.sort((m1, m2) -> SemVer.compare(m2.getVersion(), m1.getVersion()));

                PluginPackageManifest newest = manifests.get(0);
                String pluginId = newest.getId();

                List<PluginRepoResult.VersionEntry> versions = new ArrayList<>();
                for (PluginPackageManifest manifest : manifests) {
                    versions.add(PluginRepoResult.VersionEntry.from(manifest,
                            buildDownloadUrl(pluginId, manifest.getVersion())));
                }

                PluginRepoResult result = new PluginRepoResult();
                result.setId(pluginId);
                result.setName(displayName(newest));
                result.setDescription(newest.getDescription());
                result.setIcon(newest.getIcon());
                result.setKeyword(newest.getKeyword());
                result.setVersions(versions);
                pluginMap.put(pluginId, result);
            }
        }

        return new ArrayList<>(pluginMap.values());
    }

    /**
     * 导入插件包（zip：v2 清单 + 插件 JAR）。校验不通过时抛 {@link IllegalArgumentException} 并说明原因。
     */
    public PluginRepoResult importPlugin(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }

        PluginPackageManifest manifest = null;
        byte[] jsonBytes = null;
        byte[] jarBytes = null;
        Path tempDir = Files.createTempDirectory("plugin-import-");
        try {
            Path tempZip = tempDir.resolve(file.getOriginalFilename());

            // 流式写入原始ZIP文件，避免将整个文件加载到内存
            try (java.io.InputStream is = file.getInputStream()) {
                Files.copy(is, tempZip, StandardCopyOption.REPLACE_EXISTING);
            }

            // 流式读取ZIP内容，提取清单与JAR
            try (java.io.InputStream fis = Files.newInputStream(tempZip);
                 ZipInputStream zis = new ZipInputStream(fis)) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entryName.endsWith(".json")) {
                        jsonBytes = zis.readAllBytes();
                        manifest = objectMapper.readValue(jsonBytes, PluginPackageManifest.class);
                    } else if (entryName.endsWith(".jar")) {
                        jarBytes = zis.readAllBytes();
                    }
                    zis.closeEntry();
                }
            }

            if (manifest == null || jsonBytes == null) {
                throw new IllegalArgumentException("ZIP包中未找到插件清单文件（.json）");
            }
            validateManifest(manifest);

            if (jarBytes == null) {
                throw new IllegalArgumentException("ZIP包中未找到插件JAR文件");
            }

            // 校验 JAR 哈希值
            String actualJarHash = computeSha256(jarBytes);
            if (manifest.getSha256Hash() != null && !manifest.getSha256Hash().equalsIgnoreCase(actualJarHash)) {
                throw new IllegalArgumentException(String.format("JAR文件哈希校验失败！期望: %s, 实际: %s",
                        manifest.getSha256Hash(), actualJarHash));
            }

            String pluginId = sanitizeName(manifest.getId());
            String version = sanitizeName(manifest.getVersion());
            if (version == null || version.isEmpty() || "unknown".equals(version)) {
                version = "0.0.0";
            }

            Path targetDir = packagesDir.resolve(pluginId).resolve(version);
            Files.createDirectories(targetDir);

            // 流式复制ZIP文件到目标位置
            String zipFileName = pluginId + "-" + version + ".zip";
            Path targetZip = targetDir.resolve(zipFileName);
            Files.copy(tempZip, targetZip, StandardCopyOption.REPLACE_EXISTING);

            Path targetJson = targetDir.resolve(pluginId + ".json");
            Files.write(targetJson, jsonBytes);

            long zipSize = Files.size(targetZip);
            log.info("Imported plugin: [{}] v{}, JAR哈希校验通过, size: {}", pluginId, version,
                    formatFileSize(zipSize));

            PluginRepoResult result = new PluginRepoResult();
            result.setId(manifest.getId());
            result.setName(displayName(manifest));
            result.setDescription(manifest.getDescription());
            result.setIcon(manifest.getIcon());
            result.setKeyword(manifest.getKeyword());
            result.setVersions(Collections.singletonList(PluginRepoResult.VersionEntry.from(manifest,
                    buildDownloadUrl(pluginId, version))));
            return result;

        } finally {
            cleanTempDir(tempDir);
        }
    }

    public byte[] getPluginDownload(String pluginName, String version) throws IOException {
        String safePluginName = sanitizeName(pluginName);
        String safeVersion = sanitizeName(version);

        Path versionDir = packagesDir.resolve(safePluginName).resolve(safeVersion);
        if (!Files.exists(versionDir)) {
            return null;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionDir, "*.zip")) {
            for (Path zipFile : stream) {
                return Files.readAllBytes(zipFile);
            }
        }

        return null;
    }

    public void deletePlugin(String pluginName, String version) throws IOException {
        String safePluginName = sanitizeName(pluginName);
        String safeVersion = sanitizeName(version);

        Path versionDir = packagesDir.resolve(safePluginName).resolve(safeVersion);
        if (!Files.exists(versionDir)) {
            throw new IllegalArgumentException("插件版本不存在");
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionDir)) {
            for (Path p : stream) {
                Files.deleteIfExists(p);
            }
        }
        Files.deleteIfExists(versionDir);

        Path pluginDir = packagesDir.resolve(safePluginName);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginDir)) {
            if (!stream.iterator().hasNext()) {
                Files.deleteIfExists(pluginDir);
            }
        }

        log.info("Deleted plugin: [{}] v{}", safePluginName, safeVersion);
    }

    // ==================== v2 清单校验 ====================

    /** 导入链路校验：不通过直接抛错，错误信息面向使用者。 */
    private void validateManifest(PluginPackageManifest manifest) {
        if (manifest.getManifestVersion() != MANIFEST_VERSION) {
            throw new IllegalArgumentException(String.format(
                    "不支持的插件清单版本：%d，仓库只接受 v2 清单（manifestVersion=2）",
                    manifest.getManifestVersion()));
        }
        if (isBlank(manifest.getId())) {
            throw new IllegalArgumentException("插件清单缺少 id（插件标识）");
        }
        if (!manifest.hasAnyCapability()) {
            throw new IllegalArgumentException(
                    "插件清单未声明任何能力：至少需要 provides 工具集、前端资产或 invoke 之一");
        }
    }

    /**
     * 读取某版本目录下的 v2 清单；不符合 v2 规范时返回 {@code null} 并记录日志（扫描链路跳过该版本）。
     */
    private PluginPackageManifest readManifest(Path versionDir, String pluginDirId) {
        Path jsonFile = findManifestFile(versionDir, pluginDirId);
        if (jsonFile == null) {
            log.warn("插件版本目录缺少清单文件，已跳过：{}", versionDir);
            return null;
        }
        PluginPackageManifest manifest;
        try {
            manifest = objectMapper.readValue(jsonFile.toFile(), PluginPackageManifest.class);
        } catch (IOException e) {
            log.warn("插件清单解析失败，已跳过：{}", jsonFile, e);
            return null;
        }
        if (manifest.getManifestVersion() != MANIFEST_VERSION) {
            log.warn("非 v2 插件清单，已跳过：{} (manifestVersion={})", jsonFile, manifest.getManifestVersion());
            return null;
        }
        if (isBlank(manifest.getId())) {
            log.warn("插件清单缺少 id，已跳过：{}", jsonFile);
            return null;
        }
        if (!manifest.hasAnyCapability()) {
            log.warn("插件清单未声明任何能力，已跳过：{}", jsonFile);
            return null;
        }
        return manifest;
    }

    /** 找版本目录下的清单文件：优先 {@code <pluginId>.json}，否则取任意 {@code *.json}。 */
    private Path findManifestFile(Path versionDir, String pluginDirId) {
        if (pluginDirId != null && !pluginDirId.isEmpty()) {
            Path preferred = versionDir.resolve(pluginDirId + ".json");
            if (Files.exists(preferred)) {
                return preferred;
            }
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionDir, "*.json")) {
            for (Path f : stream) {
                return f;
            }
        } catch (IOException e) {
            log.warn("读取插件版本目录失败：{}", versionDir, e);
        }
        return null;
    }

    private String displayName(PluginPackageManifest manifest) {
        return isBlank(manifest.getName()) ? manifest.getId() : manifest.getName();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String sanitizeName(String name) {
        if (name == null) return "unknown";
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8")
                    .replace("+", "%20");
        } catch (Exception e) {
            return value;
        }
    }

    private void cleanTempDir(Path dir) {
        try {
            if (Files.exists(dir)) {
                Files.walk(dir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (IOException ignored) {}
                        });
            }
        } catch (IOException ignored) {}
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), pre);
    }

    private String computeSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String h = Integer.toHexString(0xff & b);
                if (h.length() == 1) hex.append('0');
                hex.append(h);
            }
            return hex.toString();
        } catch (Exception e) {
            log.error("Failed to compute SHA256", e);
            return "";
        }
    }
}
