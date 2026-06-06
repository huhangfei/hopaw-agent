package com.agent.hopaw.infra.service.tts;

import com.agent.hopaw.infra.model.dto.TtsVoice;
import com.agent.hopaw.infra.service.ITtsService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 阿里云智能语音交互 TTS 实现。
 * 配置 JSON 格式: {"accessKeyId":"xxx","accessKeySecret":"xxx","appKey":"xxx"}
 */
@Component
public class AliyunTtsService implements ITtsService {

    private static final Logger logger = LoggerFactory.getLogger(AliyunTtsService.class);
    private static final String VENDOR_CODE = "aliyun";
    private static final String VENDOR_NAME = "阿里云";

    private static final String TOKEN_URL = "https://nls-gateway.cn-shanghai.aliyuncs.com/stream/v1/tts";

    /** 缓存 token，避免频繁请求 */
    private final Map<String, TokenCache> tokenCacheMap = new HashMap<>();

    private static class TokenCache {
        String token;
        long expireTime;
        TokenCache(String token, long expireTime) {
            this.token = token;
            this.expireTime = expireTime;
        }
    }

    /** 阿里云标准音色列表 */
    private static final List<TtsVoice> BUILTIN_VOICES;

    static {
        List<TtsVoice> voices = new ArrayList<>();
        voices.add(voice("xiaoyun", "小云(标准女声)", "zh-CN", "female"));
        voices.add(voice("xiaogang", "小刚(标准男声)", "zh-CN", "male"));
        voices.add(voice("ruoxi", "若兮(温柔女声)", "zh-CN", "female"));
        voices.add(voice("siqi", "思琪(成熟女声)", "zh-CN", "female"));
        voices.add(voice("sijia", "思佳(标准女声)", "zh-CN", "female"));
        voices.add(voice("sicheng", "思诚(标准男声)", "zh-CN", "male"));
        voices.add(voice("aiqi", "艾琪(温柔女声)", "zh-CN", "female"));
        voices.add(voice("aijia", "艾佳(标准女声)", "zh-CN", "female"));
        voices.add(voice("aida", "艾达(标准女声)", "zh-CN", "female"));
        voices.add(voice("ninger", "宁儿(方言女声)", "zh-CN", "female"));
        voices.add(voice("ruilin", "瑞琳(方言女声)", "zh-CN", "female"));
        voices.add(voice("siyue", "思悦(温柔女声)", "zh-CN", "female"));
        voices.add(voice("aiya", "艾雅(粤语女声)", "yue", "female"));
        voices.add(voice("aixia", "艾夏(方言女声)", "zh-CN", "female"));
        // 英文
        voices.add(voice("harry", "Harry(英式男声)", "en-GB", "male"));
        voices.add(voice("abby", "Abby(美式女声)", "en-US", "female"));
        voices.add(voice("andy", "Andy(美式男声)", "en-US", "male"));
        voices.add(voice("eric", "Eric(美式男声)", "en-US", "male"));
        voices.add(voice("emily", "Emily(美式女声)", "en-US", "female"));
        BUILTIN_VOICES = Collections.unmodifiableList(voices);
    }

    private static TtsVoice voice(String id, String name, String lang, String gender) {
        TtsVoice v = new TtsVoice();
        v.setVoiceId(id);
        v.setVoiceName(name);
        v.setLanguage(lang);
        v.setGender(gender);
        return v;
    }

    @Override
    public String getVendorCode() {
        return VENDOR_CODE;
    }

    @Override
    public String getVendorName() {
        return VENDOR_NAME;
    }

    @Override
    public List<TtsVoice> listVoices(String configJson) {
        return BUILTIN_VOICES;
    }

    @Override
    public byte[] synthesize(String configJson, String voiceId, String text) {
        if (text == null || text.isEmpty()) {
            logger.warn("阿里云 TTS: 文本为空，跳过合成");
            return new byte[0];
        }
        try {
            JSONObject config = JSON.parseObject(configJson);
            String accessKeyId = config.getString("accessKeyId");
            String accessKeySecret = config.getString("accessKeySecret");
            String appKey = config.getString("appKey");
            if (accessKeyId == null || accessKeyId.isEmpty()
                    || accessKeySecret == null || accessKeySecret.isEmpty()
                    || appKey == null || appKey.isEmpty()) {
                logger.error("阿里云 TTS: 配置不完整");
                return new byte[0];
            }

            String token = getToken(accessKeyId, accessKeySecret);

            String urlStr = TOKEN_URL + "?appkey=" + appKey
                    + "&token=" + token
                    + "&text=" + URLEncoder.encode(text, "UTF-8")
                    + "&format=mp3"
                    + "&voice=" + voiceId
                    + "&sample_rate=16000"
                    + "&volume=50"
                    + "&speech_rate=0"
                    + "&pitch_rate=0";

            byte[] audioBytes = httpGet(urlStr);
            logger.info("阿里云 TTS 合成成功，{} 字节", audioBytes.length);
            return audioBytes;
        } catch (Exception e) {
            logger.error("阿里云 TTS 合成失败: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    private String getToken(String accessKeyId, String accessKeySecret) throws Exception {
        String cacheKey = accessKeyId + ":" + accessKeySecret;
        TokenCache cache = tokenCacheMap.get(cacheKey);
        if (cache != null && System.currentTimeMillis() < cache.expireTime - 60000) {
            return cache.token;
        }

        // 生成 token：使用阿里云 NLS token 机制
        // token = base64(json_header).base64(payload).signature
        String token = generateNlsToken(accessKeyId, accessKeySecret);
        tokenCacheMap.put(cacheKey, new TokenCache(token, System.currentTimeMillis() + 3600000));
        return token;
    }

    private String generateNlsToken(String accessKeyId, String accessKeySecret) throws Exception {
        // 通过阿里云 CreateToken API 获取 NLS Token
        // 参考: https://help.aliyun.com/document_detail/374324.html
        String domain = "nls-meta.cn-shanghai.aliyuncs.com";
        Map<String, String> params = new TreeMap<>();
        params.put("Action", "CreateToken");
        params.put("Version", "2019-02-28");
        params.put("Format", "JSON");
        params.put("AccessKeyId", accessKeyId);
        params.put("SignatureMethod", "HMAC-SHA1");
        params.put("SignatureVersion", "1.0");
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("Timestamp", getUtcTimestamp());
        params.put("RegionId", "cn-shanghai");

        // 构造签名字符串
        StringBuilder canonicalized = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) canonicalized.append('&');
            canonicalized.append(percentEncode(entry.getKey()))
                    .append('=')
                    .append(percentEncode(entry.getValue()));
            first = false;
        }

        String stringToSign = "GET&" + percentEncode("/") + "&" + percentEncode(canonicalized.toString());

        Mac mac = Mac.getInstance("HmacSHA1");
        SecretKeySpec spec = new SecretKeySpec(
                (accessKeySecret + "&").getBytes(StandardCharsets.UTF_8), "HmacSHA1");
        mac.init(spec);
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String signature = Base64.getEncoder().encodeToString(signData);

        String urlStr = "https://" + domain + "/?" + canonicalized
                + "&Signature=" + URLEncoder.encode(signature, "UTF-8");

        String responseBody = httpGetString(urlStr);
        JSONObject resp = JSON.parseObject(responseBody);
        JSONObject tokenObj = resp.getJSONObject("Token");
        if (tokenObj == null) {
            throw new RuntimeException("CreateToken 响应中无 Token 字段: " + responseBody);
        }
        return tokenObj.getString("Id");
    }

    private static String getUtcTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private static String percentEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8")
                .replace("+", "%20")
                .replace("*", "%2A")
                .replace("%7E", "~");
    }

    private String httpGetString(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new java.security.SecureRandom());
            conn.setSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception ignored) {}
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        int code = conn.getResponseCode();
        String body = readString(code == 200 ? conn.getInputStream() : conn.getErrorStream());
        conn.disconnect();
        if (code != 200) {
            throw new RuntimeException("CreateToken API 返回 " + code + ": " + body);
        }
        return body;
    }

    private byte[] httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new java.security.SecureRandom());
            conn.setSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception ignored) {}

        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String errBody = readString(conn.getErrorStream());
            logger.error("阿里云 TTS API 返回 {}: {}", responseCode, errBody);
            throw new RuntimeException("阿里云 TTS API 返回 " + responseCode + ": " + errBody);
        }

        try (InputStream is = conn.getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    private String readString(InputStream is) throws Exception {
        if (is == null) return "";
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) {
                bos.write(buf, 0, n);
            }
            return bos.toString("UTF-8");
        }
    }
}