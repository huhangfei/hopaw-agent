package com.agent.hopaw.biz.util;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * @author hhf
 */
public class QianFanWebSearchUtil {
    private static final String API_URL = "https://qianfan.baidubce.com/v2/ai_search/web_search";
    /**
     * @param apiKey
     * @param query
     * @param maxResults
     * @param timeoutMs
     * @param edition
     * @return
     * @throws Exception
     */
    public  static String search(String apiKey, String query, int maxResults, int timeoutMs,String edition) throws Exception {
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

        // 使用缓存的 edition 配置
        if (edition != null) {
            body.put("edition", edition);
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
