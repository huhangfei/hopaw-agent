package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.PluginDescriptor;
import com.agent.hopaw.infra.model.dto.PluginRepoResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.*;

@Service
public class PluginStoreService implements IPluginStoreService {
    private static final Logger log = LoggerFactory.getLogger(PluginStoreService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final ISysConfigService sysConfigService;
    private final IAgentPluginService agentPluginService;

    public PluginStoreService(ISysConfigService sysConfigService, IAgentPluginService agentPluginService) {
        this.sysConfigService = sysConfigService;
        this.agentPluginService = agentPluginService;
    }

    @Override
    public List<PluginRepoResult> fetchStorePlugins() {
        String sourceUrls = sysConfigService.getValueByKey("tool.pluginSourceUrls", "");
        if (sourceUrls.isEmpty()) {
            return Collections.emptyList();
        }
        // 已安装版本索引：pluginId 及其下所有工具集名 → 插件版本
        // （商店条目可能以 pluginId 或历史工具集名命名，两个口径都能命中判定）
        Map<String, String> installedVersions = new HashMap<>();
        for (PluginDescriptor descriptor : agentPluginService.getPlugins()) {
            installedVersions.put(descriptor.getId(), descriptor.getVersion());
            if (descriptor.getToolSetNames() != null) {
                for (String toolSetName : descriptor.getToolSetNames()) {
                    installedVersions.put(toolSetName, descriptor.getVersion());
                }
            }
        }

        List<PluginRepoResult> result = new ArrayList<>();

        for (String urlStr : sourceUrls.split(",")) {
            urlStr = urlStr.trim();
            if (urlStr.isEmpty()) continue;

            try {
                List<PluginRepoResult> storePlugins = fetchStorePlugins(urlStr);
                if (storePlugins == null) continue;

                for (PluginRepoResult storePlugin : storePlugins) {
                    String installedVersion = installedVersions.get(storePlugin.getName());
                    if (storePlugin.getVersions() != null) {
                        for (PluginRepoResult.VersionEntry version : storePlugin.getVersions()) {
                            if (installedVersion != null) {
                                if (version.getVersion().equals(installedVersion)) {
                                    version.setStatus("installed");
                                } else {
                                    version.setStatus("update_available");
                                }
                            } else {
                                version.setStatus("not_installed");
                            }
                        }
                    }
                    if (installedVersion != null) {
                        storePlugin.setInstalledVersion(installedVersion);
                    }
                    result.add(storePlugin);
                }
            } catch (Exception e) {
                log.error("Failed to fetch from store URL: {}", urlStr, e);
            }
        }
        return result;
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

}
