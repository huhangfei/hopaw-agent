package com.agent.hopaw.infra.service.tts;

import com.agent.hopaw.infra.model.dto.TtsVoice;
import com.agent.hopaw.infra.service.ITtsService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 火山引擎 TTS 实现。
 * 配置 JSON 格式: {"appId":"xxx","accessToken":"xxx","cluster":"volcano_tts"}
 */
@Component
public class VolcanoTtsService implements ITtsService {

    private static final Logger logger = LoggerFactory.getLogger(VolcanoTtsService.class);
    private static final String VENDOR_CODE = "volcano";
    private static final String VENDOR_NAME = "火山引擎";

    // 火山引擎 TTS API 地址
    private static final String TTS_URL = "https://openspeech.bytedance.com/api/v1/tts";

    /** 可选音色列表（火山引擎标准音色） */
    private static final List<TtsVoice> BUILTIN_VOICES;

    static {
        List<TtsVoice> voices = new ArrayList<>();
        voices.add(voice("BV001_streaming", "通用女声", "zh-CN", "female"));
        voices.add(voice("BV002_streaming", "通用男声", "zh-CN", "male"));
        voices.add(voice("BV003_streaming", "通用女声-温柔", "zh-CN", "female"));
        voices.add(voice("BV004_streaming", "通用男声-沉稳", "zh-CN", "male"));
        voices.add(voice("BV005_streaming", "萝莉女声", "zh-CN", "female"));
        voices.add(voice("BV006_streaming", "活泼女声", "zh-CN", "female"));
        voices.add(voice("BV007_streaming", "知性女声", "zh-CN", "female"));
        voices.add(voice("BV008_streaming", "自然男声", "zh-CN", "male"));
        // 英文音色
        voices.add(voice("BV401_streaming", "美式女声", "en-US", "female"));
        voices.add(voice("BV402_streaming", "美式男声", "en-US", "male"));
        voices.add(voice("BV403_streaming", "英式女声", "en-GB", "female"));
        voices.add(voice("BV404_streaming", "英式男声", "en-GB", "male"));
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
    public byte[] synthesize(String configJson, String voiceId, String text, String emotion) {
        if (text == null || text.isEmpty()) {
            logger.warn("火山引擎 TTS: 文本为空，跳过合成");
            return new byte[0];
        }
        try {
            JSONObject config = JSON.parseObject(configJson);
            String appId = config.getString("appId");
            String accessToken = config.getString("accessToken");
            String cluster = config.getString("cluster");
            if (cluster == null || cluster.isEmpty()) {
                cluster = "volcano_tts";
            }
            if (appId == null || appId.isEmpty() || accessToken == null || accessToken.isEmpty()) {
                logger.error("火山引擎 TTS: 配置不完整，缺少 appId 或 accessToken");
                return new byte[0];
            }

            // 构建请求体
            JSONObject request = new JSONObject();
            JSONObject app = new JSONObject();
            app.put("appid", appId);
            app.put("token", accessToken);
            app.put("cluster", cluster);
            request.put("app", app);

            JSONObject user = new JSONObject();
            user.put("uid", "hopaw-agent");
            request.put("user", user);

            JSONObject audio = new JSONObject();
            audio.put("voice_type", voiceId);
            audio.put("encoding", "mp3");
            audio.put("speed_ratio", 1.0);
            audio.put("volume_ratio", 1.0);
            audio.put("pitch_ratio", 1.0);
            request.put("audio", audio);

            JSONObject req = new JSONObject();
            req.put("reqid", System.currentTimeMillis() + "");
            req.put("text", text);
            req.put("text_type", "plain");
            req.put("operation", "submit");
            request.put("request", req);

            String json = request.toJSONString();
            logger.debug("火山引擎 TTS 请求: {}", json);

            byte[] audioBytes = httpPost(TTS_URL, json, accessToken);
            logger.info("火山引擎 TTS 合成成功，{} 字节", audioBytes.length);
            return audioBytes;
        } catch (Exception e) {
            logger.error("火山引擎 TTS 合成失败: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    private byte[] httpPost(String urlStr, String json, String accessToken) throws Exception {
        URL url = new URL(urlStr);
        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();

        // 信任所有证书
        try {
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, new java.security.SecureRandom());
            conn.setSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception ignored) {}

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer;" + accessToken);
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String errBody = readString(conn.getErrorStream());
            logger.error("火山引擎 TTS API 返回 {}: {}", responseCode, errBody);
            throw new RuntimeException("火山引擎 TTS API 返回 " + responseCode);
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