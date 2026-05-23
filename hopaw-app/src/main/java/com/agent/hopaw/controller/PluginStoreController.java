package com.agent.hopaw.controller;

import com.agent.hopaw.infra.model.dto.PluginRepoResult;
import com.agent.hopaw.infra.model.dto.ResponseBean;
import com.agent.hopaw.infra.model.dto.ToolSetInfo;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.infra.tool.IAgentToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;

@Controller
@RequestMapping("/tools/plugin-store")
public class PluginStoreController {

    private static final Logger log = LoggerFactory.getLogger(PluginStoreController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final ISysConfigService sysConfigService;
    private final IAgentToolService agentToolService;

    public PluginStoreController(ISysConfigService sysConfigService, IAgentToolService agentToolService) {
        this.sysConfigService = sysConfigService;
        this.agentToolService = agentToolService;
    }

    @GetMapping({"", "/"})
    public String storePage(Model model) {
        return "plugin-store";
    }

    @GetMapping("/api/plugins")
    @ResponseBody
    public ResponseBean apiStorePlugins() {
        String sourceUrls = sysConfigService.getValueByKey("tool.pluginSourceUrls", "");
        if (sourceUrls.isEmpty()) {
            return ResponseBean.success(Collections.emptyList());
        }

        Map<String, ToolSetInfo> installedMap = new HashMap<>();
        for (ToolSetInfo info : agentToolService.getToolSets()) {
            installedMap.put(info.getName(), info);
        }

        List<PluginStoreInfo> result = new ArrayList<>();

        for (String urlStr : sourceUrls.split(",")) {
            urlStr = urlStr.trim();
            if (urlStr.isEmpty()) continue;

            try {
                List<PluginRepoResult> storePlugins = fetchStorePlugins(urlStr);
                if (storePlugins == null) continue;

                for (PluginRepoResult storePlugin : storePlugins) {
                    PluginStoreInfo storeInfo = new PluginStoreInfo();
                    storeInfo.setName(storePlugin.getName());
                    storeInfo.setDescription(storePlugin.getDescription());
                    storeInfo.setIcon(storePlugin.getIcon());
                    storeInfo.setKeyword(storePlugin.getKeyword());

                    ToolSetInfo installed = installedMap.get(storePlugin.getName());
                    List<PluginStoreVersionInfo> versions = new ArrayList<>();

                    if (storePlugin.getVersions() != null) {
                        for (PluginRepoResult.VersionEntry version : storePlugin.getVersions()) {
                            PluginStoreVersionInfo v = new PluginStoreVersionInfo();
                            v.setVersion(version.getVersion());
                            v.setFileSize(version.getFileSize());
                            v.setSha256Hash(version.getSha256Hash());
                            v.setAuthor(version.getAuthor());
                            v.setUrl(version.getUrl());
                            v.setDownloadUrl(version.getDownloadUrl());
                            v.setTools(version.getTools());

                            if (installed != null) {
                                if (version.getVersion().equals(installed.getVersion())) {
                                    v.setStatus("installed");
                                } else {
                                    v.setStatus("update_available");
                                }
                            } else {
                                v.setStatus("not_installed");
                            }

                            versions.add(v);
                        }
                    }

                    storeInfo.setVersions(versions);

                    if (installed != null) {
                        storeInfo.setInstalledVersion(installed.getVersion());
                        storeInfo.setJarFileName(installed.getJarFileName());
                    }

                    result.add(storeInfo);
                }
            } catch (Exception e) {
                log.error("Failed to fetch from store URL: {}", urlStr, e);
            }
        }

        return ResponseBean.success(result);
    }

    private List<PluginRepoResult> fetchStorePlugins(String urlStr) {
        try {
            URL url = new URI(urlStr).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int code = conn.getResponseCode();
            if (code != 200) {
                log.warn("Store URL returned {}: {}", code, urlStr);
                conn.disconnect();
                return null;
            }

            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }
            conn.disconnect();

            return objectMapper.readValue(sb.toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PluginRepoResult.class));
        } catch (Exception e) {
            log.error("Error fetching store plugins from {}", urlStr, e);
            return null;
        }
    }

    public static class PluginStoreInfo {
        private String name;
        private String description;
        private String icon;
        private String keyword;
        private String installedVersion;
        private String jarFileName;
        private List<PluginStoreVersionInfo> versions;

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

        public String getJarFileName() { return jarFileName; }
        public void setJarFileName(String jarFileName) { this.jarFileName = jarFileName; }

        public List<PluginStoreVersionInfo> getVersions() { return versions; }
        public void setVersions(List<PluginStoreVersionInfo> versions) { this.versions = versions; }

        public boolean getIconIsSvgCode() {
            return icon != null && icon.startsWith("<svg");
        }
    }

    public static class PluginStoreVersionInfo {
        private String version;
        private long fileSize;
        private String sha256Hash;
        private String author;
        private String url;
        private String downloadUrl;
        private String status;
        private List<com.agent.hopaw.infra.model.dto.PluginToolRef> tools;

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

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public List<com.agent.hopaw.infra.model.dto.PluginToolRef> getTools() { return tools; }
        public void setTools(List<com.agent.hopaw.infra.model.dto.PluginToolRef> tools) { this.tools = tools; }
    }
}
