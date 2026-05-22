package com.agent.hopaw.infra.model.dto;

import com.agent.hopaw.infra.constant.AgentToolSourceEnum;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder({"name", "description", "keyword", "icon", "version", "author", "url",
        "source", "jarFileName", "defaultUpdateUrl", "fileSize", "sha256Hash", "tools"})
public class PluginExportInfo extends ToolSetInfo {

    private long fileSize;
    private String sha256Hash;

    public PluginExportInfo() {
        super(null, null, null, null);
    }

    public PluginExportInfo(ToolSetInfo info, long fileSize, String sha256Hash) {
        super(info.getName(), info.getDescription(), info.getIcon(), info.getTools(), info.getSource());
        this.setVersion(info.getVersion());
        this.setAuthor(info.getAuthor());
        this.setUrl(info.getUrl());
        this.setJarFileName(info.getJarFileName());
        this.setKeyword(info.getKeyword());
        this.setDefaultUpdateUrl(info.getDefaultUpdateUrl());
        this.setHasConfigItems(info.isHasConfigItems());
        this.fileSize = fileSize;
        this.sha256Hash = sha256Hash;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSha256Hash() {
        return sha256Hash;
    }

    public void setSha256Hash(String sha256Hash) {
        this.sha256Hash = sha256Hash;
    }
}