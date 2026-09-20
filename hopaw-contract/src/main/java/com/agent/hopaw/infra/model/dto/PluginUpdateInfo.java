package com.agent.hopaw.infra.model.dto;

public class PluginUpdateInfo {
    private String version;
    private String fileName;
    private long fileSize;
    private String downloadUrl;
    private String sha256Hash;
    /** 插件标识（pluginId） */
    private String pluginId;
    private String currentVersion;
    private boolean installed;
    private boolean needUpgrade;
    /**
     * 是否已显式确认安装「纯前端插件」（不提供任何工具集，仅前端资产 / invoke）。
     * 默认 false——第三方商店来源的纯前端插件必须由使用者明确确认后才允许安装。
     */
    private boolean allowFrontendOnly;

    public PluginUpdateInfo() {
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public boolean isInstalled() {
        return installed;
    }

    public void setInstalled(boolean installed) {
        this.installed = installed;
    }

    public boolean isNeedUpgrade() {
        return needUpgrade;
    }

    public void setNeedUpgrade(boolean needUpgrade) {
        this.needUpgrade = needUpgrade;
    }

    public boolean isAllowFrontendOnly() {
        return allowFrontendOnly;
    }

    public void setAllowFrontendOnly(boolean allowFrontendOnly) {
        this.allowFrontendOnly = allowFrontendOnly;
    }
}
