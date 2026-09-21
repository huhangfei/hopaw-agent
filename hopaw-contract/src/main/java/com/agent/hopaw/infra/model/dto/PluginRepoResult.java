package com.agent.hopaw.infra.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件仓库条目（**以插件为主体**）：一个条目 = 一个插件 + N 个版本。
 * 与插件包清单 {@link PluginPackageManifest} v2 一一对应，仓库只认 v2。
 *
 * <p>注意字段语义：{@link #id} 是插件标识（pluginId，等于清单 {@code id}，也是仓库目录名），
 * {@link #name} 只是展示名。所有安装/判定/寻址都用 {@code id}。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginRepoResult {

    /** 插件标识（pluginId） */
    private String id;
    /** 插件展示名 */
    private String name;
    private String description;
    private String icon;
    private String keyword;

    /** 已安装版本（Agent 侧填充，仓库侧为 null） */
    private String installedVersion;

    private List<VersionEntry> versions = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getInstalledVersion() { return installedVersion; }
    public void setInstalledVersion(String installedVersion) { this.installedVersion = installedVersion; }

    public List<VersionEntry> getVersions() { return versions; }
    public void setVersions(List<VersionEntry> versions) { this.versions = versions; }

    public boolean getIconIsSvgCode() {
        return icon != null && icon.startsWith("<svg");
    }

    /**
     * 版本条目：对应一个插件包（zip = 清单 + JAR），声明该插件提供了什么。
     *
     * <p>清单里只带工具集**摘要**（名称/描述/工具方法数），完整参数明细由安装后扫描 {@code @Tool} 得到。</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VersionEntry {

        /** 清单版本，必须为 2 */
        private int manifestVersion;
        private String version;
        private long fileSize;
        private String sha256Hash;
        private String author;
        private String url;
        private String downloadUrl;
        private String jarFileName;

        /** 提供的工具集摘要（0..N；纯前端插件为空）；每项含方法明细 methods */
        private List<PluginPackageManifest.ProvidedToolSet> provides = new ArrayList<>();
        /** 前端资产数量（与 frontendAssets 长度一致，摘要字段） */
        private int frontendAssetCount;
        /** 前端资产明细（名称 / JAR 内路径 / 类型 / 大小） */
        private List<PluginPackageManifest.ProvidedAsset> frontendAssets = new ArrayList<>();
        /** 是否提供 invoke 能力 */
        private boolean invokeSupport;
        /** 插件级配置项数量 */
        private int configItemCount;

        /** Agent 侧填充：installed / update_available / not_installed */
        private String status;

        public int getManifestVersion() { return manifestVersion; }
        public void setManifestVersion(int manifestVersion) { this.manifestVersion = manifestVersion; }

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }

        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }

        public String getSha256Hash() { return sha256Hash; }
        public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }

        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }

        public String getDownloadUrl() { return downloadUrl; }
        public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }

        public String getJarFileName() { return jarFileName; }
        public void setJarFileName(String jarFileName) { this.jarFileName = jarFileName; }

        public List<PluginPackageManifest.ProvidedToolSet> getProvides() { return provides; }
        public void setProvides(List<PluginPackageManifest.ProvidedToolSet> provides) { this.provides = provides; }

        public int getFrontendAssetCount() { return frontendAssetCount; }
        public void setFrontendAssetCount(int frontendAssetCount) { this.frontendAssetCount = frontendAssetCount; }

        public List<PluginPackageManifest.ProvidedAsset> getFrontendAssets() { return frontendAssets; }
        public void setFrontendAssets(List<PluginPackageManifest.ProvidedAsset> frontendAssets) { this.frontendAssets = frontendAssets; }

        public boolean isInvokeSupport() { return invokeSupport; }
        public void setInvokeSupport(boolean invokeSupport) { this.invokeSupport = invokeSupport; }

        public int getConfigItemCount() { return configItemCount; }
        public void setConfigItemCount(int configItemCount) { this.configItemCount = configItemCount; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        // ==================== 展示辅助 ====================

        /** 工具集数量 */
        public int getToolSetCount() {
            return provides == null ? 0 : provides.size();
        }

        /** 是否含前端资产 */
        public boolean isHasFrontendAssets() {
            return frontendAssetCount > 0;
        }

        /** 是否为纯前端插件（无工具集） */
        public boolean isFrontendOnly() {
            return getToolSetCount() == 0;
        }

        public boolean isHasConfigItems() {
            return configItemCount > 0;
        }

        /** 由 v2 清单构建版本条目。 */
        public static VersionEntry from(PluginPackageManifest manifest, String downloadUrl) {
            VersionEntry entry = new VersionEntry();
            entry.setManifestVersion(manifest.getManifestVersion());
            entry.setVersion(manifest.getVersion());
            entry.setFileSize(manifest.getFileSize());
            entry.setSha256Hash(manifest.getSha256Hash());
            entry.setAuthor(manifest.getAuthor());
            entry.setUrl(manifest.getUrl());
            entry.setDownloadUrl(downloadUrl);
            entry.setJarFileName(manifest.getJarFileName());
            entry.setProvides(manifest.getProvides() == null
                    ? new ArrayList<>() : new ArrayList<>(manifest.getProvides()));
            entry.setFrontendAssetCount(manifest.getFrontendAssetCount());
            entry.setFrontendAssets(manifest.getFrontendAssets() == null
                    ? new ArrayList<>() : new ArrayList<>(manifest.getFrontendAssets()));
            entry.setInvokeSupport(manifest.isInvokeSupport());
            entry.setConfigItemCount(manifest.getConfigItems() == null ? 0 : manifest.getConfigItems().size());
            return entry;
        }
    }
}
