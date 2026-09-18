package com.agent.hopaw.tool.http;

import com.agent.hopaw.infra.tool.ToolSecurityLevel;
import com.agent.hopaw.infra.tool.AgentTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP请求工具集
 * 支持GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS等常见HTTP方法，用于接口调用测试
 */
public class HttpTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(HttpTool.class);

    private static final int DEFAULT_TIMEOUT = 30000;

    @Override
    public String getName() {
        return "httpTool";
    }

    @Override
    public String getDescription() {
        return "HTTP请求工具集，支持GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS等常见HTTP方法，用于接口调用测试";
    }

    @Override
    public String getIcon() {
        return "http-tool.svg";
    }

    @Override
    public String getKeyword() {
        return "http,接口,api,request";
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"发送HTTP GET请求", "发送HTTP GET请求，用于查询接口数据"})
    public String doHttpGet(
            @P(description = "请求URL，如 https://api.example.com/users") String url,
            @P(description = "请求头，JSON格式，如 {\"Authorization\":\"Bearer xxx\"}", required = false) String headers,
            @P(description = "请求超时时间(毫秒)，默认30000", required = false) Integer timeout) {
        return executeRequest("GET", url, null, headers, timeout);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"发送HTTP POST请求", "发送HTTP POST请求，用于提交数据或创建资源"})
    public String doHttpPost(
            @P(description = "请求URL") String url,
            @P(description = "请求体内容，如JSON字符串") String body,
            @P(description = "请求头，JSON格式", required = false) String headers,
            @P(description = "Content-Type，默认application/json", required = false) String contentType,
            @P(description = "请求超时时间(毫秒)，默认30000", required = false) Integer timeout) {
        return executeRequest("POST", url, body, headers, timeout, contentType);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"发送HTTP PUT请求", "发送HTTP PUT请求，用于更新资源"})
    public String doHttpPut(
            @P(description = "请求URL") String url,
            @P(description = "请求体内容") String body,
            @P(description = "请求头，JSON格式", required = false) String headers,
            @P(description = "Content-Type，默认application/json", required = false) String contentType,
            @P(description = "请求超时时间(毫秒)，默认30000", required = false) Integer timeout) {
        return executeRequest("PUT", url, body, headers, timeout, contentType);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"发送HTTP DELETE请求", "发送HTTP DELETE请求，用于删除资源"})
    public String doHttpDelete(
            @P(description = "请求URL") String url,
            @P(description = "请求头，JSON格式", required = false) String headers,
            @P(description = "请求超时时间(毫秒)，默认30000", required = false) Integer timeout) {
        return executeRequest("DELETE", url, null, headers, timeout);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"发送HTTP PATCH请求", "发送HTTP PATCH请求，用于部分更新资源"})
    public String doHttpPatch(
            @P(description = "请求URL") String url,
            @P(description = "请求体内容") String body,
            @P(description = "请求头，JSON格式", required = false) String headers,
            @P(description = "Content-Type，默认application/json", required = false) String contentType,
            @P(description = "请求超时时间(毫秒)，默认30000", required = false) Integer timeout) {
        return executeRequest("PATCH", url, body, headers, timeout, contentType);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"发送HTTP HEAD请求", "发送HTTP HEAD请求，用于获取响应头信息"})
    public String doHttpHead(
            @P(description = "请求URL") String url,
            @P(description = "请求头，JSON格式", required = false) String headers,
            @P(description = "请求超时时间(毫秒)，默认30000", required = false) Integer timeout) {
        return executeRequest("HEAD", url, null, headers, timeout);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.SAFE)
    @Tool(value = {"发送HTTP OPTIONS请求", "发送HTTP OPTIONS请求，用于查询支持的HTTP方法"})
    public String doHttpOptions(
            @P(description = "请求URL") String url,
            @P(description = "请求头，JSON格式", required = false) String headers,
            @P(description = "请求超时时间(毫秒)，默认30000", required = false) Integer timeout) {
        return executeRequest("OPTIONS", url, null, headers, timeout);
    }

    @ToolSecurityLevel(ToolSecurityLevel.Level.PARAM_REQUIRE_APPROVAL)
    @Tool(value = {"发送自定义HTTP请求", "发送自定义方法的HTTP请求，支持任意HTTP方法"})
    public String doHttpRequest(
            @P(description = "HTTP方法，如 GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS") String method,
            @P(description = "请求URL") String url,
            @P(description = "请求体内容(POST/PUT/PATCH时使用)", required = false) String body,
            @P(description = "请求头，JSON格式", required = false) String headers,
            @P(description = "Content-Type，默认application/json", required = false) String contentType,
            @P(description = "请求超时时间(毫秒)，默认30000", required = false) Integer timeout) {
        return executeRequest(method.toUpperCase(), url, body, headers, timeout, contentType);
    }

    private String executeRequest(String method, String url, String body, String headers, Integer timeout) {
        return executeRequest(method, url, body, headers, timeout, null);
    }

    private String executeRequest(String method, String url, String body, String headers, Integer timeout, String contentType) {
        long start = System.currentTimeMillis();
        HttpURLConnection conn = null;
        try {
            if (url == null || url.isBlank()) {
                return "错误: URL不能为空";
            }

            URL targetUrl = new URL(url);
            conn = (HttpURLConnection) targetUrl.openConnection();
            conn.setConnectTimeout(timeout != null ? timeout : DEFAULT_TIMEOUT);
            conn.setReadTimeout(timeout != null ? timeout : DEFAULT_TIMEOUT);
            conn.setDoOutput(true);
            conn.setDoInput(true);

            // PATCH 方法需要特殊处理：HttpURLConnection 不原生支持
            if ("PATCH".equals(method)) {
                conn.setRequestMethod("POST");
                conn.setRequestProperty("X-HTTP-Method-Override", "PATCH");
                try {
                    Field methodField = HttpURLConnection.class.getDeclaredField("method");
                    methodField.setAccessible(true);
                    methodField.set(conn, "PATCH");
                } catch (Exception ignored) {
                    // 反射失败时使用 X-HTTP-Method-Override
                }
            } else {
                conn.setRequestMethod(method);
            }

            // 设置请求头
            if (headers != null && !headers.isBlank()) {
                try {
                    Map<String, String> headerMap = com.alibaba.fastjson2.JSON.parseObject(headers,
                            new com.alibaba.fastjson2.TypeReference<Map<String, String>>() {});
                    for (Map.Entry<String, String> entry : headerMap.entrySet()) {
                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) {
                    return "错误: 请求头JSON格式错误 - " + e.getMessage();
                }
            }

            // 设置Content-Type
            String ct = contentType != null && !contentType.isBlank() ? contentType : "application/json";
            if (body != null && !body.isBlank()) {
                conn.setRequestProperty("Content-Type", ct);
            }

            // 写入请求体
            if (body != null && !body.isBlank()) {
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
                    os.write(bodyBytes);
                    os.flush();
                }
            }

            // 读取响应
            int responseCode = conn.getResponseCode();
            String responseBody = readStream(
                    responseCode >= 200 && responseCode < 400
                            ? conn.getInputStream()
                            : conn.getErrorStream());

            long elapsed = System.currentTimeMillis() - start;

            // 构建结果
            StringBuilder sb = new StringBuilder();
            sb.append("=== HTTP ").append(method).append(" 响应 ===\n");
            sb.append("URL: ").append(url).append("\n");
            sb.append("状态码: ").append(responseCode).append("\n");
            sb.append("耗时: ").append(elapsed).append("ms\n");

            // 响应头
            Map<String, List<String>> responseHeaders = conn.getHeaderFields();
            if (responseHeaders != null && !responseHeaders.isEmpty()) {
                sb.append("--- 响应头 ---\n");
                for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
                    if (entry.getKey() != null) {
                        sb.append(entry.getKey()).append(": ").append(String.join(", ", entry.getValue())).append("\n");
                    }
                }
            }

            sb.append("--- 响应体 ---\n");
            if (responseBody != null && !responseBody.isEmpty()) {
                // 截断过长的响应体
                if (responseBody.length() > 5000) {
                    sb.append(responseBody, 0, 5000).append("\n... (响应体过长，已截断，共 ")
                            .append(responseBody.length()).append(" 字符)");
                } else {
                    sb.append(responseBody);
                }
            } else {
                sb.append("(空)");
            }

            log.info("HTTP {} {} -> {} ({}ms)", method, url, responseCode, elapsed);
            return sb.toString();

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("HTTP {} {} 失败 ({}ms)", method, url, elapsed, e);
            return "错误: HTTP请求失败 - " + e.getMessage();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String readStream(java.io.InputStream is) {
        if (is == null) {
            return null;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            return "读取响应失败: " + e.getMessage();
        }
    }
}
