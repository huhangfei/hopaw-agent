package com.agent.hopaw.infra.service.tts;

import com.agent.hopaw.infra.constant.TtsEmotionEnum;
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
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * MiMo-V2.5-TTS 语音合成实现（小米 MiMo 大模型 TTS 系列）。
 * 使用 OpenAI 兼容的 chat/completions 接口。
 * <p>
 * 配置 JSON 格式: {"apiKey":"xxx","baseUrl":"https://api.xiaomimimo.com/v1"}
 * <p>
 * 支持三种模型：
 * <ul>
 *   <li>mimo-v2.5-tts — 预置精品音色</li>
 *   <li>mimo-v2.5-tts-voicedesign — 文本描述设计音色</li>
 *   <li>mimo-v2.5-tts-voiceclone — 音频样本复刻音色</li>
 * </ul>
 */
@Component
public class MiMoTtsService implements ITtsService {

    private static final Logger logger = LoggerFactory.getLogger(MiMoTtsService.class);
    private static final String VENDOR_CODE = "mimo";
    private static final String VENDOR_NAME = "MiMo TTS";

    private static final String DEFAULT_BASE_URL = "https://api.xiaomimimo.com/v1";
    private static final String DEFAULT_MODEL = "mimo-v2.5-tts";

    /** 预置精品音色列表 */
    private static final List<TtsVoice> BUILTIN_VOICES;

    /** 标准情感 → MiMo 中文风格标签映射 */
    private static final java.util.Map<TtsEmotionEnum, String> EMOTION_STYLE_MAP;

    static {
        List<TtsVoice> v = new ArrayList<>();
        List<String> emoCodes = java.util.Arrays.stream(TtsEmotionEnum.values())
                .map(TtsEmotionEnum::getCode).toList();
        v.add(voiceEmo("mimo_default", "MiMo-默认", "zh-CN", "female", emoCodes));
        v.add(voiceEmo("冰糖", "冰糖", "zh-CN", "female", emoCodes));
        v.add(voiceEmo("茉莉", "茉莉", "zh-CN", "female", emoCodes));
        v.add(voiceEmo("苏打", "苏打", "zh-CN", "male", emoCodes));
        v.add(voiceEmo("白桦", "白桦", "zh-CN", "male", emoCodes));
        v.add(voiceEmo("Mia", "Mia", "en-US", "female", emoCodes));
        v.add(voiceEmo("Chloe", "Chloe", "en-US", "female", emoCodes));
        v.add(voiceEmo("Milo", "Milo", "en-US", "male", emoCodes));
        v.add(voiceEmo("Dean", "Dean", "en-US", "male", emoCodes));
        BUILTIN_VOICES = Collections.unmodifiableList(v);

        EMOTION_STYLE_MAP = new java.util.EnumMap<>(TtsEmotionEnum.class);
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.NEUTRAL, "平静");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.HAPPY, "开心");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.SAD, "悲伤");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.ANGRY, "愤怒");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.FEAR, "恐惧");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.SURPRISE, "惊讶");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.HATE, "厌恶");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.AROUSAL, "激动");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.DISGUST, "反感");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.JEALOUSY, "嫉妒");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.EMBARRASSED, "尴尬");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.FRUSTRATED, "沮丧");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.AFFECTIONATE, "深情");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.GENTLE, "温柔");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.SERIOUS, "严肃");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.EXCITED, "兴奋");
        EMOTION_STYLE_MAP.put(TtsEmotionEnum.LIVELY, "活泼");
    }

    private static TtsVoice voice(String id, String name, String lang, String gender) {
        TtsVoice v = new TtsVoice();
        v.setVoiceId(id);
        v.setVoiceName(name);
        v.setLanguage(lang);
        v.setGender(gender);
        return v;
    }

    private static TtsVoice voiceEmo(String id, String name, String lang, String gender, List<String> emotions) {
        TtsVoice v = voice(id, name, lang, gender);
        v.setEmotions(emotions);
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
    public byte[] synthesize(String configJson, String voiceId, String text, TtsEmotionEnum emotion) {
        if (text == null || text.isEmpty()) {
            logger.warn("MiMo TTS: 文本为空，跳过合成");
            return new byte[0];
        }
        try {
            JSONObject config = JSON.parseObject(configJson);
            String apiKey = config.getString("apiKey");
            String baseUrl = config.getString("baseUrl");
            if (apiKey == null || apiKey.isEmpty()) {
                logger.error("MiMo TTS: 配置不完整，缺少 apiKey");
                return new byte[0];
            }
            if (baseUrl == null || baseUrl.isEmpty()) {
                baseUrl = DEFAULT_BASE_URL;
            }
            String model = config.getString("model");
            if (model == null || model.isEmpty()) {
                model = DEFAULT_MODEL;
            }

            // 构建 messages
            JSONArray messages = new JSONArray();

            // user 消息：风格控制指令
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            if (emotion != null) {
                String style = EMOTION_STYLE_MAP.getOrDefault(emotion, emotion.getName());
                userMsg.put("content", style);
            } else {
                userMsg.put("content", "");
            }
            messages.add(userMsg);

            // assistant 消息：待合成文本
            JSONObject assistantMsg = new JSONObject();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", text);
            messages.add(assistantMsg);

            // 构建请求体
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("messages", messages);

            JSONObject audio = new JSONObject();
            audio.put("format", "wav");
            if (voiceId != null && !voiceId.isEmpty()) {
                audio.put("voice", voiceId);
            }
            body.put("audio", audio);

            String json = body.toJSONString();
            logger.debug("MiMo TTS 请求: model={} voice={} text={}", model, voiceId,
                    text.length() > 50 ? text.substring(0, 50) + "..." : text);

            // 发送请求
            String urlStr = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
            JSONObject resp = httpPostJson(urlStr, json, apiKey);

            // 解析响应，提取 base64 音频
            JSONArray choices = resp.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                logger.error("MiMo TTS: 响应中无 choices");
                return new byte[0];
            }
            JSONObject choice = choices.getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            if (message == null) {
                logger.error("MiMo TTS: 响应中无 message");
                return new byte[0];
            }
            JSONObject audioObj = message.getJSONObject("audio");
            if (audioObj == null) {
                logger.error("MiMo TTS: 响应中无 audio");
                return new byte[0];
            }
            String audioData = audioObj.getString("data");
            if (audioData == null || audioData.isEmpty()) {
                logger.error("MiMo TTS: audio.data 为空");
                return new byte[0];
            }

            byte[] audioBytes = Base64.getDecoder().decode(audioData);
            logger.info("MiMo TTS 合成成功，{} 字节", audioBytes.length);
            return audioBytes;
        } catch (Exception e) {
            logger.error("MiMo TTS 合成失败: {}", e.getMessage(), e);
            return new byte[0];
        }
    }

    private JSONObject httpPostJson(String urlStr, String jsonBody, String apiKey) throws Exception {
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
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setDoOutput(true);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            os.flush();
        }

        int responseCode = conn.getResponseCode();
        String responseBody;
        if (responseCode == 200) {
            responseBody = readString(conn.getInputStream());
        } else {
            responseBody = readString(conn.getErrorStream());
            logger.error("MiMo TTS API 返回 {}: {}", responseCode, responseBody);
            throw new RuntimeException("MiMo TTS API 返回 " + responseCode + ": " + responseBody);
        }
        conn.disconnect();

        return JSON.parseObject(responseBody);
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
