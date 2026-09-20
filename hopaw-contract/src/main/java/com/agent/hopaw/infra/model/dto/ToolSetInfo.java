package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.constant.AgentToolSourceEnum;
import com.agent.hopaw.infra.tool.AgentTool;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public class ToolSetInfo {
    private String name;
    private String description;
    private String keyword;
    private String icon;
    private List<ToolInfo> tools;
    private AgentToolSourceEnum source;
    private String version;
    private String author;
    private String url;
    private String jarFileName;
    /** 所属插件标识（内置工具为 null） */
    private String pluginId;
    /** 所属插件显示名称（内置工具为 null） */
    private String pluginName;
    private boolean hasConfigItems;
    /** 是否启用：false 表示工具集级禁用（未记录视为 true）。仅做标记，不影响展示，用于智能体选择与执行过滤 */
    private boolean enabled = true;

    @JsonIgnore
    private AgentTool agentTool;

    public Boolean iconIsSvgCode(){
        return icon != null && icon.startsWith("<svg");
    }

    /** 可用（未禁用）工具方法数 */
    public int getEnabledToolCount() {
        if (tools == null) {
            return 0;
        }
        return (int) tools.stream().filter(ToolInfo::isEnabled).count();
    }

    /** 禁用工具方法数 */
    public int getDisabledToolCount() {
        int total = tools == null ? 0 : tools.size();
        return total - getEnabledToolCount();
    }

    public ToolSetInfo() {
    }

    public ToolSetInfo(String name, String description, String icon, List<ToolInfo> tools) {
        this(name, description, icon, tools, AgentToolSourceEnum.BUILT_IN);
    }

    public ToolSetInfo(String name, String description, String icon, List<ToolInfo> tools, AgentToolSourceEnum source) {
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.tools = tools;
        this.source = source;
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getIcon() { return icon; }
    public List<ToolInfo> getTools() { return tools; }
    public AgentToolSourceEnum getSource() { return source; }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setTools(List<ToolInfo> tools) {
        this.tools = tools;
    }

    public void setSource(AgentToolSourceEnum source) {
        this.source = source;
    }

    public String getJarFileName() {
        return jarFileName;
    }

    public void setJarFileName(String jarFileName) {
        this.jarFileName = jarFileName;
    }

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public String getPluginName() {
        return pluginName;
    }

    public void setPluginName(String pluginName) {
        this.pluginName = pluginName;
    }

    public boolean isHasConfigItems() {
        return hasConfigItems;
    }

    public void setHasConfigItems(boolean hasConfigItems) {
        this.hasConfigItems = hasConfigItems;
    }

    public AgentTool getAgentTool() {
        return agentTool;
    }

    public void setAgentTool(AgentTool agentTool) {
        this.agentTool = agentTool;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
