package com.agent.hopaw.tool.baidusearch;

import com.agent.hopaw.infra.model.dto.OptionItem;
import com.agent.hopaw.infra.model.dto.ToolConfigItem;
import com.agent.hopaw.infra.model.dto.ValidationRule;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.service.ISysConfigService;
import com.agent.hopaw.infra.tool.AgentTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class BaiduSearchTool implements AgentTool {
    private static final String CONFIG_KEY_API_KEYS = "apiKeys";
    private static final String CONFIG_KEY_EDITION = "edition";
    private static final Logger log = LoggerFactory.getLogger(BaiduSearchTool.class);
    private final AtomicInteger keyIndex = new AtomicInteger(0);

    private volatile List<String> cachedApiKeys = Collections.emptyList();
    private volatile String cachedEdition = null;

    private static final int TIMEOUT_MS = 10000;
    private static final int MAX_RESULTS = 5;

    @Autowired
    private ISysConfigService sysConfigService;

    @Tool(value = {"搜索查询互联网最新网络信息，返回相关的网页标题和摘要内容。", "新闻、军事、财经、时事、天气、资料"})
    public String baiduSearch(@P(description = "搜索关键词") String query, @P(description = "最大数，默认5", required = false) Integer maxResults, @P(description = "超时时间（毫秒），默认10000毫秒", required = false) Integer timeout) {
        if (query == null || query.trim().isEmpty()) {
            return "错误: 搜索关键词不能为空";
        }
        if (cachedApiKeys.isEmpty()) {
            reloadConfig();
        }

        String apiKey = selectKey(cachedApiKeys);
        if (apiKey == null) {
            return "错误: 没有配置百度千帆 API 密钥";
        }
        String edition = cachedEdition;

        int mr = maxResults != null ? maxResults : MAX_RESULTS;
        int to = timeout != null ? timeout : TIMEOUT_MS;

        try {
            String result = QianFanWebSearchUtil.search(apiKey, query, mr, to, edition);
            if (result != null) {
                return result;
            }
        } catch (Exception e) {
            return "搜索失败: " + e.getMessage();
        }
        return "未找到相关结果";
    }

    @Override
    public String getName() {
        return "baiduSearch";
    }

    @Override
    public String getDescription() {
        return "百度搜索网页信息，返回相关的网页标题和摘要内容";
    }

    @Override
    public String getIcon() {
        return "web-search-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "搜索,百度,千帆,联网,web,搜索,查找,查询";
    }

    @Override
    public List<ToolConfigItem> getConfigItems() {
        return List.of(
                new ToolConfigItem("apiKeys", "API 密钥", "百度千帆 API 密钥，支持多个密钥自动轮询使用", ToolConfigItem.ConfigType.TEXT_PASSWORD_MULTI)
                        .validation(new ValidationRule().required()),
                new ToolConfigItem("edition", "搜索版本", "选择搜索版本", ToolConfigItem.ConfigType.SELECT,
                        new OptionItem("standard", "完整版"),
                        new OptionItem("lite", "轻量版"))
                        .validation(new ValidationRule().required())
        );
    }

    @Override
    public void asyncInit() {
        String prefix = getConfigPrefix();
        sysConfigService.setSensitiveKeys(prefix + CONFIG_KEY_API_KEYS);
        reloadConfig();
    }

    @Override
    public void onConfigChanged() {
        log.info("收到配置变更通知，重新加载百度搜索工具配置");
        reloadConfig();
    }

    public void reloadConfig() {
        this.cachedApiKeys = loadApiKeys();
        this.cachedEdition = loadEdition();
        this.keyIndex.set(0);
        log.info("百度搜索工具配置已重载");
    }

    private List<String> loadApiKeys() {
        String prefix = getConfigPrefix();
        SysConfig config = sysConfigService.getByKey(prefix + CONFIG_KEY_API_KEYS);
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(config.getConfigValue().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String loadEdition() {
        String prefix = getConfigPrefix();
        SysConfig config = sysConfigService.getByKey(prefix + CONFIG_KEY_EDITION);
        if (config != null && config.getConfigValue() != null && !config.getConfigValue().isBlank()) {
            return config.getConfigValue();
        }
        return null;
    }

    private String selectKey(List<String> keys) {
        if (keys.isEmpty()) return null;
        int index = keyIndex.getAndUpdate(i -> (i + 1) % keys.size());
        return keys.get(index);
    }
}