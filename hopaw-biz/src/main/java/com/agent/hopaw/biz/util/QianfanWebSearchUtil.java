package com.agent.hopaw.biz.util;

import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.service.SysConfigService;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class QianfanWebSearchUtil {

    private static final Logger log = LoggerFactory.getLogger(QianfanWebSearchUtil.class);

    private static final String CONFIG_KEY = "qianfan_web_search_api_keys";
    private static final String CONFIG_KEY_EDITION = "qianfan_web_search_edition";
    private static final String API_URL = "https://qianfan.baidubce.com/v2/ai_search/web_search";
    private static final int TIMEOUT_SECONDS = 15;

    private final SysConfigService sysConfigService;
    private final AtomicInteger keyIndex = new AtomicInteger(0);

    public QianfanWebSearchUtil(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    private List<String> getApiKeys() {
        SysConfig config = sysConfigService.getByKey(CONFIG_KEY);
        if (config == null || config.getConfigValue() == null || config.getConfigValue().isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(config.getConfigValue().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private String selectKey(List<String> keys) {
        if (keys.isEmpty()) return null;
        int index = keyIndex.getAndUpdate(i -> (i + 1) % keys.size());
        return keys.get(index);
    }

    /**
     * 搜索互联网信息，返回格式化的搜索结果文本
     *
     * @param query      搜索关键词
     * @param maxResults 最大返回结果数（1-50，默认20）
     * @param timeoutMs  请求超时时间（毫秒）
     * @return 格式化搜索结果，未配置密钥时返回错误提示
     */
    public String search(String query, int maxResults, int timeoutMs) {
        List<String> keys = getApiKeys();
        if (keys.isEmpty()) {
            throw new RuntimeException("未配置API密钥");
        }

        int maxAttempts = keys.size();
        for (int i = 0; i < maxAttempts; i++) {
            String apiKey = selectKey(keys);
            try {
                return doSearch(apiKey, query, maxResults, timeoutMs);
            } catch (Exception e) {
                log.warn("Qianfan搜索失败(密钥{}): {}", i + 1, e.getMessage());
                if (i == maxAttempts - 1) {
                    throw new RuntimeException("所有API密钥均无效", e);
                }
            }
        }
        return "搜索失败: 所有API密钥均无效";
    }

    /**
     * 搜索互联网信息，使用默认参数
     */
    public String search(String query) {
        return search(query, 20, TIMEOUT_SECONDS * 1000);
    }

    private String doSearch(String apiKey, String query, int maxResults, int timeoutMs) throws Exception {
        int clampedResults = Math.max(1, Math.min(maxResults, 50));
        Duration timeout = Duration.ofMillis(Math.max(1000, timeoutMs));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();

        JSONObject body = new JSONObject();
        JSONArray messages = new JSONArray();
        JSONObject msg = new JSONObject();
        msg.put("role", "user");
        msg.put("content", query);
        messages.add(msg);
        body.put("messages", messages);
        body.put("search_source", "baidu_search_v2");

        // 读取 edition 配置并传入请求
        SysConfig editionConfig = sysConfigService.getByKey(CONFIG_KEY_EDITION);
        if (editionConfig != null && editionConfig.getConfigValue() != null && !editionConfig.getConfigValue().isBlank()) {
            body.put("edition", editionConfig.getConfigValue());
        }

        JSONArray resourceFilter = new JSONArray();
        JSONObject webFilter = new JSONObject();
        webFilter.put("type", "web");
        webFilter.put("top_k", clampedResults);
        resourceFilter.add(webFilter);
        body.put("resource_type_filter", resourceFilter);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .timeout(timeout)
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("HTTP " + response.statusCode() + ": " + response.body());
        }

        JSONObject json = JSONObject.parseObject(response.body());

        if (json.containsKey("code") && json.getIntValue("code") != 0) {
            throw new RuntimeException("错误码" + json.getIntValue("code") + ": " + json.getString("message"));
        }

        JSONArray references = json.getJSONArray("references");
        if (references == null || references.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < references.size(); i++) {
            JSONObject ref = references.getJSONObject(i);
            sb.append("[").append(ref.getIntValue("id")).append("] ");
            sb.append(ref.getString("title")).append("\n");
            sb.append("链接: ").append(ref.getString("url")).append("\n");
            String content = ref.getString("content");
            if (content != null) {
                sb.append("摘要: ").append(content).append("\n");
            }
            String date = ref.getString("date");
            if (date != null) {
                sb.append("发布时间: ").append(date).append("\n");
            }
            String website = ref.getString("website");
            if (website != null) {
                sb.append("站点: ").append(website).append("\n");
            }
            sb.append("\n");
        }

        return sb.toString().trim();
    }
}
