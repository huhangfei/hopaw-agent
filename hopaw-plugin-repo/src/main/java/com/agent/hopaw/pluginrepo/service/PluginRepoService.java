package com.agent.hopaw.pluginrepo.service;

import com.agent.hopaw.infra.model.dto.PluginExportInfo;
import com.agent.hopaw.infra.model.dto.PluginRepoResult;
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
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class PluginRepoService {

    private static final Logger log = LoggerFactory.getLogger(PluginRepoService.class);

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
                String pluginName = pluginDir.getFileName().toString();

                try (DirectoryStream<Path> versionDirs = Files.newDirectoryStream(pluginDir)) {
                    List<PluginRepoResult.VersionEntry> versions = new ArrayList<>();
                    String description = null;
                    String icon = null;
                    String keyword = null;

                    for (Path versionDir : versionDirs) {
                        if (!Files.isDirectory(versionDir)) {
                            continue;
                        }
                        String version = versionDir.getFileName().toString();

                        PluginExportInfo info = parsePluginInfo(versionDir, pluginName);
                        if (info == null) {
                            continue;
                        }

                        if (description == null && info.getDescription() != null) {
                            description = info.getDescription();
                        }
                        if (icon == null && info.getIcon() != null) {
                            icon = info.getIcon();
                        }
                        if (keyword == null && info.getKeyword() != null) {
                            keyword = info.getKeyword();
                        }

                        String base = getBaseUrl();
                        String query = getQueryString();
                        String relPath = "/plugin-repo/api/download/"
                                + urlEncode(pluginName) + "/" + urlEncode(version);
                        String downloadUrl = (base != null ? base : "") + relPath + query;
                        PluginRepoResult.VersionEntry entry = PluginRepoResult.VersionEntry.from(info, downloadUrl);
                        versions.add(entry);
                    }

                    if (!versions.isEmpty()) {
                        versions.sort(Comparator.comparing(PluginRepoResult.VersionEntry::getVersion).reversed());

                        PluginRepoResult result = new PluginRepoResult();
                        result.setName(pluginName);
                        result.setDescription(description);
                        result.setIcon(icon);
                        result.setKeyword(keyword);
                        result.setVersions(versions);
                        pluginMap.put(pluginName, result);
                    }
                }
            }
        }

        return new ArrayList<>(pluginMap.values());
    }

    public PluginRepoResult importPlugin(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }

        byte[] originalBytes = file.getBytes();

        PluginExportInfo exportInfo = null;
        byte[] jsonBytes = null;
        Path tempDir = Files.createTempDirectory("plugin-import-");
        try {
            Path tempZip = tempDir.resolve(file.getOriginalFilename());
            Files.write(tempZip, originalBytes);

            try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(originalBytes))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entryName.endsWith(".json")) {
                        jsonBytes = zis.readAllBytes();
                        exportInfo = objectMapper.readValue(jsonBytes, PluginExportInfo.class);
                    }
                    zis.closeEntry();
                }
            }

            if (exportInfo == null || jsonBytes == null) {
                cleanTempDir(tempDir);
                throw new IllegalArgumentException("ZIP包中未找到插件描述文件");
            }

            String pluginName = sanitizeName(exportInfo.getName());
            String version = sanitizeName(exportInfo.getVersion());
            if (version == null || version.isEmpty()) {
                version = "0.0.0";
            }

            Path targetDir = packagesDir.resolve(pluginName).resolve(version);
            Files.createDirectories(targetDir);

            String zipFileName = pluginName + "-" + version + ".zip";
            Path targetZip = targetDir.resolve(zipFileName);
            Files.write(targetZip, originalBytes);

            Path targetJson = targetDir.resolve(pluginName + ".json");
            Files.write(targetJson, jsonBytes);

            log.info("Imported plugin: {} v{}, size: {}", pluginName, version,
                    formatFileSize(originalBytes.length));

            PluginRepoResult result = new PluginRepoResult();
            result.setName(pluginName);
            result.setDescription(exportInfo.getDescription());
            PluginRepoResult.VersionEntry ve = PluginRepoResult.VersionEntry.from(exportInfo,
                    "/plugin-repo/api/download/" + urlEncode(pluginName) + "/" + urlEncode(version));
            result.setVersions(Collections.singletonList(ve));
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

        log.info("Deleted plugin: {} v{}", safePluginName, safeVersion);
    }

    private PluginExportInfo parsePluginInfo(Path versionDir, String pluginName) {
        Path jsonFile = versionDir.resolve(pluginName + ".json");
        if (!Files.exists(jsonFile)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionDir, "*.json")) {
                for (Path f : stream) {
                    jsonFile = f;
                    break;
                }
            } catch (IOException e) {
                log.warn("Failed to find JSON in {}", versionDir, e);
                return null;
            }
        }

        if (!Files.exists(jsonFile)) {
            return null;
        }

        try {
            return objectMapper.readValue(jsonFile.toFile(), PluginExportInfo.class);
        } catch (IOException e) {
            log.warn("Failed to parse plugin info from {}", jsonFile, e);
            return null;
        }
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
}