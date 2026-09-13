package com.agent.hopaw.infra.service.tts;

import com.agent.hopaw.infra.model.dto.TtsVoice;
import com.agent.hopaw.infra.service.ITtsService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Piper TTS 实现（自部署的 Piper HTTP 服务，无需凭证）。
 * 配置 JSON 格式: {"baseUrl":"http://<Piper服务地址>:5500"}
 */
@Component
public class PiperTtsService implements ITtsService {

    private static final Logger logger = LoggerFactory.getLogger(PiperTtsService.class);
    private static final String VENDOR_CODE = "piper";
    private static final String VENDOR_NAME = "Piper";

    /** Piper 服务标准音色列表（远程 /voices 查询失败时的回退） */
    private static final List<TtsVoice> BUILTIN_VOICES;

    /** 已知音色的友好名称，未命中时回退使用 voiceId 本身 */
    private static final Map<String, String> VOICE_NAMES = new HashMap<>();

    /** 已知音色的性别，未命中时为 null（接口不返回性别信息） */
    private static final Map<String, String> VOICE_GENDERS = new HashMap<>();

    static {
        VOICE_NAMES.put("zh_CN-huayan-medium", "华燕(中文女声)");
        VOICE_NAMES.put("en_US-lessac-medium", "Lessac(英文男声)");
        VOICE_GENDERS.put("zh_CN-huayan-medium", "female");
        VOICE_GENDERS.put("en_US-lessac-medium", "male");

        List<TtsVoice> v = new ArrayList<>();
        v.add(voice("zh_CN-huayan-medium", VOICE_NAMES.get("zh_CN-huayan-medium"), "zh-CN", "female"));
        v.add(voice("en_US-lessac-medium", VOICE_NAMES.get("en_US-lessac-medium"), "en-US", "male"));
        BUILTIN_VOICES = Collections.unmodifiableList(v);
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
        String baseUrl = parseBaseUrl(configJson);
        if (baseUrl == null) {
            return BUILTIN_VOICES;
        }
        try {
            String body = httpGetString(baseUrl + "/voices");
            JSONObject resp = JSON.parseObject(body);
            JSONArray voices = resp.getJSONArray("voices");
            if (voices == null || voices.isEmpty()) {
                return BUILTIN_VOICES;
            }
            List<TtsVoice> result = new ArrayList<>();
            for (int i = 0; i < voices.size(); i++) {
                String voiceId = voices.getString(i);
                if (voiceId == null || voiceId.isBlank()) {
                    continue;
                }
                result.add(toVoice(voiceId));
            }
            if (result.isEmpty()) {
                return BUILTIN_VOICES;
            }
            logger.info("Piper TTS 从 /voices 查询到 {} 个音色", result.size());
            return result;
        } catch (Exception e) {
            logger.warn("Piper TTS 查询 /voices 失败，使用默认音色: {}", e.getMessage());
            return BUILTIN_VOICES;
        }
    }

    /** 从配置 JSON 解析 baseUrl，缺失或为空返回 null */
    private String parseBaseUrl(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return null;
        }
        try {
            JSONObject config = JSON.parseObject(configJson);
            String baseUrl = config.getString("baseUrl");
            if (baseUrl == null || baseUrl.isBlank()) {
                return null;
            }
            return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        } catch (Exception e) {
            logger.warn("Piper TTS 配置解析失败: {}", e.getMessage());
            return null;
        }
    }

    /** 将远程返回的 voiceId（如 zh_CN-huayan-medium）转换为 TtsVoice */
    private TtsVoice toVoice(String voiceId) {
        TtsVoice v = new TtsVoice();
        v.setVoiceId(voiceId);
        v.setVoiceName(VOICE_NAMES.getOrDefault(voiceId, voiceId));
        v.setGender(VOICE_GENDERS.get(voiceId));
        int idx = voiceId.indexOf('-');
        if (idx > 0) {
            v.setLanguage(voiceId.substring(0, idx).replace('_', '-'));
        } else {
            v.setLanguage("");
        }
        return v;
    }

    @Override
    public byte[] synthesize(String configJson, String voiceId, String text, String emotion) {
        if (text == null || text.isEmpty()) {
            logger.warn("Piper TTS: 文本为空，跳过合成");
            return new byte[0];
        }
        try {
            String baseUrl = parseBaseUrl(configJson);
            if (baseUrl == null) {
                logger.error("Piper TTS: 配置不完整，缺少 baseUrl");
                return new byte[0];
            }

            // Piper 不支持情感参数，emotion 忽略
            JSONObject body = new JSONObject();
            body.put("text", text);
            if (voiceId != null && !voiceId.isBlank()) {
                body.put("voice", voiceId);
            }

            byte[] audioBytes = httpPostJson(baseUrl + "/tts", body.toJSONString());
            logger.info("Piper TTS 合成成功，{} 字节", audioBytes.length);
            return audioBytes;
        } catch (Exception e) {
            logger.error("Piper TTS 合成失败: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    /** GET 请求，返回响应文本（JSON） */
    private String httpGetString(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        try {
            int code = conn.getResponseCode();
            if (code != 200) {
                String errBody = readString(conn.getErrorStream());
                logger.error("Piper TTS GET {} 返回 {}: {}", urlStr, code, errBody);
                throw new RuntimeException("Piper TTS GET 返回 " + code + ": " + errBody);
            }
            return readString(conn.getInputStream());
        } finally {
            conn.disconnect();
        }
    }

    /** POST JSON 请求，返回响应二进制（WAV 音频） */
    private byte[] httpPostJson(String urlStr, String jsonBody) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(60000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }

        int responseCode = conn.getResponseCode();
        if (responseCode != 200) {
            String errBody = readString(conn.getErrorStream());
            logger.error("Piper TTS API 返回 {}: {}", responseCode, errBody);
            throw new RuntimeException("Piper TTS API 返回 " + responseCode + ": " + errBody);
        }

        try (InputStream is = conn.getInputStream();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
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
