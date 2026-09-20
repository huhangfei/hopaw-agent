package com.agent.hopaw.infra.model.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 插件包清单 v2：以插件（AgentPlugin）为主体的唯一清单格式。
 *
 * <p>用于插件导出包（zip：本清单 + 插件 JAR）与插件仓库条目，声明一个插件提供了什么：
 * 元数据 + 0..N 个工具集（{@link ProvidedToolSet}）+ 前端资产数 + invoke 能力 + 插件级配置项。</p>
 */
public class PluginPackageManifest {

    /** 清单版本，当前固定 2 */
    private int manifestVersion = 2;

    /** 插件标识（pluginId） */
    private String id;
    private String name;
    private String description;
    private String version;
    private String author;
    private String url;
    /** 插件图标（jar 内文件名或 svg 代码） */
    private String icon;
    private String keyword;

    private String jarFileName;
    private long fileSize;
    private String sha256Hash;

    /** 提供的工具集清单（0..N） */
    private List<ProvidedToolSet> provides = new ArrayList<>();
    /** 前端资产数量 */
    private int frontendAssetCount;
    /** 是否提供 invoke 能力 */
    private boolean invokeSupport;
    /** 插件级配置项定义 */
    private List<ToolConfigItem> configItems = new ArrayList<>();

    /**
     * 工具集声明：清单里只带摘要信息（名称/描述/工具方法数），
     * 完整参数明细由安装后的 {@code @Tool} 扫描提供。
     */
    public static class ProvidedToolSet {
        private String name;
        private String description;
        private int toolCount;

        public ProvidedToolSet() {
        }

        public ProvidedToolSet(String name, String description, int toolCount) {
            this.name = name;
            this.description = description;
            this.toolCount = toolCount;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public int getToolCount() { return toolCount; }
        public void setToolCount(int toolCount) { this.toolCount = toolCount; }
    }

    /**
     * 是否声明了任何能力（工具集 / 前端资产 / invoke）。
     * 三者皆无的插件包视为无效。
     */
    public boolean hasAnyCapability() {
        return (provides != null && !provides.isEmpty()) || frontendAssetCount > 0 || invokeSupport;
    }

    public int getManifestVersion() { return manifestVersion; }
    public void setManifestVersion(int manifestVersion) { this.manifestVersion = manifestVersion; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public String getJarFileName() { return jarFileName; }
    public void setJarFileName(String jarFileName) { this.jarFileName = jarFileName; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public String getSha256Hash() { return sha256Hash; }
    public void setSha256Hash(String sha256Hash) { this.sha256Hash = sha256Hash; }

    public List<ProvidedToolSet> getProvides() { return provides; }
    public void setProvides(List<ProvidedToolSet> provides) { this.provides = provides; }

    public int getFrontendAssetCount() { return frontendAssetCount; }
    public void setFrontendAssetCount(int frontendAssetCount) { this.frontendAssetCount = frontendAssetCount; }

    public boolean isInvokeSupport() { return invokeSupport; }
    public void setInvokeSupport(boolean invokeSupport) { this.invokeSupport = invokeSupport; }

    public List<ToolConfigItem> getConfigItems() { return configItems; }
    public void setConfigItems(List<ToolConfigItem> configItems) { this.configItems = configItems; }
}
