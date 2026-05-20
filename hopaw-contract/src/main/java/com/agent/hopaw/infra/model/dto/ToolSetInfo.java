package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.constant.AgentToolSourceEnum;

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
    private boolean hasConfigItems;

    public Boolean iconIsSvgCode(){
        return icon != null && icon.startsWith("<svg");
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

    public boolean isHasConfigItems() {
        return hasConfigItems;
    }

    public void setHasConfigItems(boolean hasConfigItems) {
        this.hasConfigItems = hasConfigItems;
    }
}
