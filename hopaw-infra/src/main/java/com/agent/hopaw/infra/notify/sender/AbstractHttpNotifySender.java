package com.agent.hopaw.infra.notify.sender;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 基于 HTTP 的通知发送器公共基类：提供 JSON 配置解析与 POST JSON 能力。
 */
public abstract class AbstractHttpNotifySender {

    protected final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** 解析渠道配置 JSON，空配置返回空对象 */
    protected JSONObject parseConfig(String config) {
        if (config == null || config.isBlank()) {
            return new JSONObject();
        }
        try {
            return JSON.parseObject(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("渠道配置不是合法的 JSON: " + e.getMessage());
        }
    }

    /**
     * POST JSON 请求。
     *
     * @return null=成功（2xx），否则为错误描述
     */
    protected String postJson(String url, String jsonBody, java.util.Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
            if (headers != null) {
                headers.forEach(builder::header);
            }
            HttpResponse<String> resp = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 200 && resp.statusCode() < 300) {
                return null;
            }
            return "HTTP " + resp.statusCode() + ": " + brief(resp.body());
        } catch (IOException e) {
            return "请求失败: " + e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "请求被中断: " + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "无效的请求地址: " + e.getMessage();
        }
    }

    /** 响应体截断，避免日志与错误信息过长 */
    protected String brief(String text) {
        if (text == null) {
            return "";
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() > 200 ? t.substring(0, 200) + "..." : t;
    }
}
