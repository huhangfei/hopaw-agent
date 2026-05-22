package com.agent.hopaw.infra.model.dto;

import java.util.List;

public class PluginRepoResult {

    private String name;
    private String description;
    private String icon;
    private String keyword;
    private List<VersionEntry> versions;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getKeyword() { return keyword; }
    public void setKeyword(String keyword) { this.keyword = keyword; }

    public List<VersionEntry> getVersions() { return versions; }
    public void setVersions(List<VersionEntry> versions) { this.versions = versions; }

    public boolean getIconIsSvgCode() {
        return icon != null && icon.startsWith("<svg");
    }

    public static class VersionEntry {
        private String version;
        private long fileSize;
        private String sha256Hash;
        private String author;
        private String url;
        private String downloadUrl;
        private List<PluginToolRef> tools;

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

        public List<PluginToolRef> getTools() { return tools; }
        public void setTools(List<PluginToolRef> tools) { this.tools = tools; }

        public static VersionEntry from(PluginExportInfo info, String downloadUrl) {
            VersionEntry entry = new VersionEntry();
            entry.setVersion(info.getVersion());
            entry.setFileSize(info.getFileSize());
            entry.setSha256Hash(info.getSha256Hash());
            entry.setAuthor(info.getAuthor());
            entry.setUrl(info.getUrl());
            entry.setDownloadUrl(downloadUrl);
            if (info.getTools() != null) {
                entry.setTools(info.getTools().stream()
                        .map(t -> {
                            PluginToolRef ref = new PluginToolRef(t.getName(), t.getDescription());
                            if (t.getParameters() != null) {
                                ref.setParameters(t.getParameters().stream()
                                        .map(p -> new PluginToolRef.ParamRef(
                                                p.getName(), p.getType(), p.getDescription(), p.isRequired()))
                                        .toList());
                            }
                            return ref;
                        })
                        .toList());
            }
            return entry;
        }
    }
}