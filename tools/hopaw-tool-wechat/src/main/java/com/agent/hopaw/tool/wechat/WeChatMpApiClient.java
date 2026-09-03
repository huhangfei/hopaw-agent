package com.agent.hopaw.tool.wechat;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 微信公众平台 API 客户端。
 * <p>多公众号支持：access_token 按 appId 缓存，有效期 7200 秒，提前 300 秒刷新；
 * 遇 40001/42001（token 无效/过期）自动刷新重试一次。</p>
 */
class WeChatMpApiClient {

    private static final Logger log = LoggerFactory.getLogger(WeChatMpApiClient.class);

    private static final String API_BASE = "https://api.weixin.qq.com/cgi-bin/";
    /** token 提前刷新余量（毫秒） */
    private static final long TOKEN_REFRESH_AHEAD_MS = 300_000L;
    /** token 请求/业务请求超时（毫秒） */
    private static final int TIMEOUT_MS = 15_000;

    private final HttpClient httpClient;

    /** appId → 缓存 token */
    private static final Map<String, CachedToken> TOKEN_CACHE = new ConcurrentHashMap<>();

    WeChatMpApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
                .build();
    }

    private static class CachedToken {
        final String token;
        final long expireAt;

        CachedToken(String token, long expireAt) {
            this.token = token;
            this.expireAt = expireAt;
        }

        boolean valid() {
            return token != null && System.currentTimeMillis() < expireAt;
        }
    }

    /** API 业务异常（errcode != 0） */
    static class WxApiException extends RuntimeException {
        private final int errcode;

        WxApiException(int errcode, String errmsg) {
            super("errcode=" + errcode + ", errmsg=" + errmsg);
            this.errcode = errcode;
        }

        int getErrcode() {
            return errcode;
        }
    }

    /**
     * 获取（或刷新）access_token
     */
    private String getAccessToken(String appId, String appSecret) {
        CachedToken cached = TOKEN_CACHE.get(appId);
        if (cached != null && cached.valid()) {
            return cached.token;
        }
        synchronized (WeChatMpApiClient.class) {
            cached = TOKEN_CACHE.get(appId);
            if (cached != null && cached.valid()) {
                return cached.token;
            }
            String url = API_BASE + "token?grant_type=client_credential"
                    + "&appid=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
                    + "&secret=" + URLEncoder.encode(appSecret, StandardCharsets.UTF_8);
            JSONObject resp = executeGet(url);
            if (resp.containsKey("errcode") && resp.getIntValue("errcode") != 0) {
                throw new WxApiException(resp.getIntValue("errcode"),
                        "获取access_token失败: " + resp.getString("errmsg")
                                + "（请检查 appId/appSecret 是否正确，以及 IP 是否在公众号白名单内）");
            }
            String token = resp.getString("access_token");
            Integer expiresIn = resp.getInteger("expires_in");
            if (token == null || token.isEmpty()) {
                throw new WxApiException(-1, "获取access_token失败: 响应缺少 access_token");
            }
            long ttl = (expiresIn != null ? expiresIn : 7200) * 1000L;
            TOKEN_CACHE.put(appId, new CachedToken(token, System.currentTimeMillis() + ttl - TOKEN_REFRESH_AHEAD_MS));
            return token;
        }
    }

    /** 使指定公众号的 token 缓存失效 */
    private void invalidateToken(String appId) {
        TOKEN_CACHE.remove(appId);
    }

    /**
     * 业务 GET 请求（带 token，token 失效自动刷新重试一次）
     */
    JSONObject get(String appId, String appSecret, String pathAndQuery) {
        String token = getAccessToken(appId, appSecret);
        String url = API_BASE + pathAndQuery + (pathAndQuery.contains("?") ? "&" : "?") + "access_token=" + token;
        try {
            return executeGet(url);
        } catch (WxApiException e) {
            if (isTokenError(e.getErrcode())) {
                invalidateToken(appId);
                return get(appId, appSecret, pathAndQuery);
            }
            throw e;
        }
    }

    /**
     * 业务 POST 请求（JSON body，带 token，token 失效自动刷新重试一次）
     */
    JSONObject post(String appId, String appSecret, String path, Object body) {
        String token = getAccessToken(appId, appSecret);
        String url = API_BASE + path + "?access_token=" + token;
        try {
            return executePost(url, body);
        } catch (WxApiException e) {
            if (isTokenError(e.getErrcode())) {
                invalidateToken(appId);
                return post(appId, appSecret, path, body);
            }
            throw e;
        }
    }

    /**
     * 上传素材（multipart/form-data，字段名 media，带 token，token 失效自动刷新重试一次）
     *
     * @param path       接口路径（如 material/add_material、media/uploadimg）
     * @param extraQuery 附加查询参数（如 type=image），可为 null
     */
    JSONObject uploadMedia(String appId, String appSecret, String path, String extraQuery, Path file) throws IOException, InterruptedException {
        String token = getAccessToken(appId, appSecret);
        String url = API_BASE + path + "?access_token=" + token
                + (extraQuery != null && !extraQuery.isEmpty() ? "&" + extraQuery : "");
        try {
            return executeUpload(url, file);
        } catch (WxApiException e) {
            if (isTokenError(e.getErrcode())) {
                invalidateToken(appId);
                return uploadMedia(appId, appSecret, path, extraQuery, file);
            }
            throw e;
        }
    }

    // ========== HTTP 基础执行 ==========

    private JSONObject executeGet(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WxApiException(-1, "请求被中断");
        } catch (IOException e) {
            throw new WxApiException(-1, "网络请求失败: " + e.getMessage());
        }
    }

    private JSONObject executePost(String url, Object body) {
        try {
            String json = body instanceof String ? (String) body : JSONObject.toJSONString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parseResponse(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WxApiException(-1, "请求被中断");
        } catch (IOException e) {
            throw new WxApiException(-1, "网络请求失败: " + e.getMessage());
        }
    }

    /**
     * multipart/form-data 上传（字段名 media，自动追加 filename 与 content-type）
     */
    private JSONObject executeUpload(String url, Path file) throws IOException, InterruptedException {
        String boundary = "----hopawwx" + System.currentTimeMillis();
        String filename = file.getFileName().toString();
        String contentType = guessContentType(filename);

        byte[] fileBytes = Files.readAllBytes(file);

        // 组装 multipart body
        String head = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"media\"; filename=\"" + filename + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        String tail = "\r\n--" + boundary + "--\r\n";

        byte[] headBytes = head.getBytes(StandardCharsets.UTF_8);
        byte[] tailBytes = tail.getBytes(StandardCharsets.UTF_8);
        byte[] bodyBytes = new byte[headBytes.length + fileBytes.length + tailBytes.length];
        int pos = 0;
        System.arraycopy(headBytes, 0, bodyBytes, pos, headBytes.length);
        pos += headBytes.length;
        System.arraycopy(fileBytes, 0, bodyBytes, pos, fileBytes.length);
        pos += fileBytes.length;
        System.arraycopy(tailBytes, 0, bodyBytes, pos, tailBytes.length);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(60_000))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(List.of(bodyBytes)))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return parseResponse(response.body());
    }

    private static String guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".bmp")) return "image/bmp";
        return "image/jpeg";
    }

    private JSONObject parseResponse(String body) {
        if (body == null || body.isEmpty()) {
            throw new WxApiException(-1, "微信接口返回空响应");
        }
        JSONObject json;
        try {
            json = JSONObject.parseObject(body);
        } catch (Exception e) {
            // 个别错误场景返回非 JSON 纯文本
            throw new WxApiException(-1, "微信接口返回非 JSON 响应: " + truncate(body, 200));
        }
        Integer errcode = json.getInteger("errcode");
        if (errcode != null && errcode != 0) {
            throw new WxApiException(errcode, json.getString("errmsg"));
        }
        return json;
    }

    private static boolean isTokenError(int errcode) {
        // 40001: invalid credential / 42001: access_token expired / 40014: invalid access_token
        return errcode == 40001 || errcode == 42001 || errcode == 40014;
    }

    static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...(已截断)";
    }

    /** 便捷方法：数组字段安全取值 */
    static JSONArray getArray(JSONObject obj, String key) {
        JSONArray arr = obj.getJSONArray(key);
        return arr != null ? arr : new JSONArray();
    }
}
