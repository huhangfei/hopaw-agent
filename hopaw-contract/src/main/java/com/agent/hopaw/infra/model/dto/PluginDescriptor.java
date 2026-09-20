package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.plugin.AgentPlugin;

/**
 * 插件描述符：插件管理（一级）视图的聚合 DTO。
 *
 * <p>对应插件清单 v2：一个插件 = 元数据 + 0..N 工具集 + 可选前端资产 + 可选 invoke 能力。</p>
 */
public class PluginDescriptor {

    /** 插件唯一标识 */
    private String id;
    /** 显示名称 */
    private String name;
    /** 描述 */
    private String description;
    private String version;
    private String author;
    private String url;
    private String icon;
    private String keyword;
    /** 插件提供的工具集名列表 */
    private String[] toolSetNames;
    /** 工具集数量 */
    private int toolCount;
    /** 前端资产数量 */
    private int frontendAssetCount;
    /** 是否提供 invoke 能力 */
    private boolean invokeSupported;
    /** 是否启用（plugin_state，未启用时工具不进 ToolSets、前端资产不注入） */
    private boolean enabled = true;
    /** 物理来源 JAR 文件名（仅定位用，不再是插件标识） */
    private String jarFileName;
    /** 是否有插件级配置项 */
    private boolean hasConfigItems;

    /**
     * 从插件实例构建基础描述符（启用状态、资产数等运行时信息由服务层补充）。
     */
    public static PluginDescriptor of(AgentPlugin plugin) {
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setId(plugin.getId());
        descriptor.setName(plugin.getName());
        descriptor.setDescription(plugin.getDescription());
        descriptor.setVersion(plugin.getVersion());
        descriptor.setAuthor(plugin.getAuthor());
        descriptor.setUrl(plugin.getUrl());
        descriptor.setIcon(plugin.getIcon());
        descriptor.setKeyword(plugin.getKeyword());
        descriptor.setHasConfigItems(!plugin.getConfigItems().isEmpty());
        return descriptor;
    }

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

    public String[] getToolSetNames() { return toolSetNames; }
    public void setToolSetNames(String[] toolSetNames) { this.toolSetNames = toolSetNames; }

    public int getToolCount() { return toolCount; }
    public void setToolCount(int toolCount) { this.toolCount = toolCount; }

    public int getFrontendAssetCount() { return frontendAssetCount; }
    public void setFrontendAssetCount(int frontendAssetCount) { this.frontendAssetCount = frontendAssetCount; }

    public boolean isInvokeSupported() { return invokeSupported; }
    public void setInvokeSupported(boolean invokeSupported) { this.invokeSupported = invokeSupported; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getJarFileName() { return jarFileName; }
    public void setJarFileName(String jarFileName) { this.jarFileName = jarFileName; }

    public boolean isHasConfigItems() { return hasConfigItems; }
    public void setHasConfigItems(boolean hasConfigItems) { this.hasConfigItems = hasConfigItems; }
}
