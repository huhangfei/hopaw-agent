package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.PluginDescriptor;
import com.agent.hopaw.infra.model.dto.PluginRepoResult;
import com.agent.hopaw.infra.util.SemVer;
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
        // 已安装版本索引：插件标识（pluginId）→ 已安装版本
        // 条目与已安装插件统一走 pluginId 口径（不再按工具集名做别名匹配）
        Map<String, String> installedVersions = new HashMap<>();
        for (PluginDescriptor descriptor : agentPluginService.getPlugins()) {
            if (descriptor.getId() != null) {
                installedVersions.put(descriptor.getId(), descriptor.getVersion());
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
                    String installedVersion = installedVersions.get(storePlugin.getId());
                    if (storePlugin.getVersions() != null) {
                        for (PluginRepoResult.VersionEntry version : storePlugin.getVersions()) {
                            version.setStatus(resolveVersionStatus(installedVersion, version.getVersion()));
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

    /**
     * 判定某仓库版本相对已安装版本的状态。
     *
     * <ul>
     *   <li>{@code installed}：与已安装版本一致；</li>
     *   <li>{@code update_available}：比已安装版本新（语义化比较，{@code 1.10.0} 大于 {@code 1.9.0}）；</li>
     *   <li>{@code older}：比已安装版本旧（可回退，但不应提示为"可更新"）；</li>
     *   <li>{@code not_installed}：该插件尚未安装。</li>
     * </ul>
     */
    private String resolveVersionStatus(String installedVersion, String candidateVersion) {
        if (installedVersion == null || installedVersion.isEmpty()) {
            return "not_installed";
        }
        int cmp = SemVer.compare(candidateVersion, installedVersion);
        if (cmp == 0) {
            // 含 1.2 与 1.2.0 这类"写法不同但语义相等"的情况
            return "installed";
        }
        return cmp > 0 ? "update_available" : "older";
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
