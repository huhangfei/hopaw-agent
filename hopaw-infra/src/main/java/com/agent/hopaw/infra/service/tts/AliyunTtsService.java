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

    private static final List<String> EMO_ZHIFENG = Arrays.asList("angry", "fear", "happy", "neutral", "sad", "surprise");
    private static final List<String> EMO_ZHIMIAO = Arrays.asList("serious", "sad", "disgust", "jealousy", "embarrassed", "happy", "fear", "surprise", "neutral", "frustrated", "affectionate", "gentle", "angry", "newscast", "customer-service", "story", "living");
    private static final List<String> EMO_ZHIMI = Arrays.asList("angry", "fear", "happy", "hate", "neutral", "sad", "surprise");
    private static final List<String> EMO_ZHIYAN = Arrays.asList("neutral", "happy", "angry", "sad", "fear", "hate", "surprise", "arousal");
    private static final List<String> EMO_ZHIBEI = Arrays.asList("neutral", "happy", "angry", "sad", "fear", "hate", "surprise");
    private static final List<String> EMO_ZHITIAN_EMO = Arrays.asList("neutral", "happy", "angry", "sad", "fear", "hate", "surprise");

    static {
        List<TtsVoice> v = new ArrayList<>();
        // ========== 对话数字人 ==========
        v.add(voice("abin", "阿斌(广东普通话)", "zh-CN", "male"));
        v.add(voice("zhixiaobai", "知小白(普通话女声)", "zh-CN", "female"));
        v.add(voice("zhixiaoxia", "知小夏(普通话女声)", "zh-CN", "female"));
        v.add(voice("zhixiaomei", "知小妹(普通话女声)", "zh-CN", "female"));
        v.add(voice("zhigui", "知柜(普通话女声)", "zh-CN", "female"));
        v.add(voice("zhishuo", "知硕(普通话男声)", "zh-CN", "male"));
        v.add(voice("aixia", "艾夏(普通话女声)", "zh-CN", "female"));
        v.add(voice("cally", "Cally(美式英文女声)", "en-US", "female"));
        // ========== 多情感声音 ==========
        v.add(voiceEmo("zhifeng_emo", "知锋_多情感(男声)", "zh-CN", "male", EMO_ZHIFENG));
        v.add(voiceEmo("zhibing_emo", "知冰_多情感(男声)", "zh-CN", "male", EMO_ZHIFENG));
        v.add(voiceEmo("zhimiao_emo", "知妙_多情感(女声)", "zh-CN", "female", EMO_ZHIMIAO));
        v.add(voiceEmo("zhimi_emo", "知米_多情感(女声)", "zh-CN", "female", EMO_ZHIMI));
        v.add(voiceEmo("zhiyan_emo", "知燕_多情感(女声)", "zh-CN", "female", EMO_ZHIYAN));
        v.add(voiceEmo("zhibei_emo", "知贝_多情感(童声)", "zh-CN", "child", EMO_ZHIBEI));
        v.add(voiceEmo("zhitian_emo", "知甜_多情感(女声)", "zh-CN", "female", EMO_ZHITIAN_EMO));
        // ========== Lite 版 ==========
        v.add(voice("xiaoyun", "小云(标准女声)", "zh-CN", "female"));
        v.add(voice("xiaogang", "小刚(标准男声)", "zh-CN", "male"));
        // ========== 通用场景 - 标准版 ==========
        v.add(voice("ruoxi", "若兮(温柔女声)", "zh-CN", "female"));
        v.add(voice("siqi", "思琪(温柔女声)", "zh-CN", "female"));
        v.add(voice("sijia", "思佳(标准女声)", "zh-CN", "female"));
        v.add(voice("sicheng", "思诚(标准男声)", "zh-CN", "male"));
        v.add(voice("aiqi", "艾琪(温柔女声)", "zh-CN", "female"));
        v.add(voice("aijia", "艾佳(标准女声)", "zh-CN", "female"));
        v.add(voice("aicheng", "艾诚(标准男声)", "zh-CN", "male"));
        v.add(voice("aida", "艾达(标准男声)", "zh-CN", "male"));
        v.add(voice("ninger", "宁儿(标准女声)", "zh-CN", "female"));
        v.add(voice("ruilin", "瑞琳(标准女声)", "zh-CN", "female"));
        v.add(voice("zhitian", "知甜(甜美女声)", "zh-CN", "female"));
        v.add(voice("zhiqi", "知琪(温柔女声)", "zh-CN", "female"));
        v.add(voice("zhijia", "知佳(标准女声)", "zh-CN", "female"));
        v.add(voice("zhinan", "知楠(广告男声)", "zh-CN", "male"));
        v.add(voice("zhiqian", "知倩(资讯女声)", "zh-CN", "female"));
        v.add(voice("zhiru", "知茹(新闻女声)", "zh-CN", "female"));
        v.add(voice("zhide", "知德(新闻男声)", "zh-CN", "male"));
        v.add(voice("zhixiang", "知祥(磁性男声)", "zh-CN", "male"));
        v.add(voice("zhifei", "知飞(激昂解说)", "zh-CN", "male"));
        v.add(voice("zhilun", "知伦(悬疑解说)", "zh-CN", "male"));
        v.add(voice("zhiwei", "知薇(萝莉女声)", "zh-CN", "female"));
        v.add(voice("zhichu", "知厨(舌尖男声)", "zh-CN", "male"));
        v.add(voice("zhiyuan", "知媛(普通话女声)", "zh-CN", "female"));
        v.add(voice("zhiya", "知雅(普通话女声)", "zh-CN", "female"));
        v.add(voice("zhiyue", "知悦(普通话女声)", "zh-CN", "female"));
        v.add(voice("zhida", "知达(普通话男声)", "zh-CN", "male"));
        v.add(voice("zhistella", "知莎(普通话女声)", "zh-CN", "female"));
        v.add(voice("zhimao", "知猫(普通话女声)", "zh-CN", "female"));
        v.add(voice("stella", "Stella(知性女声)", "zh-CN", "female"));
        v.add(voice("stanley", "Stanley(沉稳男声)", "zh-CN", "male"));
        v.add(voice("kenny", "Kenny(沉稳男声)", "zh-CN", "male"));
        v.add(voice("rosa", "Rosa(自然女声)", "zh-CN", "female"));
        v.add(voice("mashu", "马树(儿童剧男声)", "zh-CN", "male"));
        v.add(voice("yuer", "悦儿(儿童剧女声)", "zh-CN", "female"));
        v.add(voice("guijie", "柜姐(亲切女声)", "zh-CN", "female"));
        // ========== 客服场景 ==========
        v.add(voice("siyue", "思悦(温柔女声)", "zh-CN", "female"));
        v.add(voice("aiya", "艾雅(严厉女声)", "zh-CN", "female"));
        v.add(voice("aimei", "艾美(甜美女声)", "zh-CN", "female"));
        v.add(voice("aiyu", "艾雨(自然女声)", "zh-CN", "female"));
        v.add(voice("aiyue", "艾悦(温柔女声)", "zh-CN", "female"));
        v.add(voice("aijing", "艾婧(严厉女声)", "zh-CN", "female"));
        v.add(voice("xiaomei", "小美(甜美女声)", "zh-CN", "female"));
        v.add(voice("aina", "艾娜(浙普女声)", "zh-CN", "female"));
        v.add(voice("yina", "伊娜(浙普女声)", "zh-CN", "female"));
        v.add(voice("sijing", "思婧(严厉女声)", "zh-CN", "female"));
        v.add(voice("aishuo", "艾硕(自然男声)", "zh-CN", "male"));
        // ========== 文学场景 - 精品版 ==========
        v.add(voice("aiyuan", "艾媛(知心姐姐)", "zh-CN", "female"));
        v.add(voice("aiying", "艾颖(软萌童声)", "zh-CN", "female"));
        v.add(voice("aixiang", "艾祥(磁性男声)", "zh-CN", "male"));
        v.add(voice("aimo", "艾墨(情感男声)", "zh-CN", "male"));
        v.add(voice("aiye", "艾晔(青年男声)", "zh-CN", "male"));
        v.add(voice("aiting", "艾婷(电台女声)", "zh-CN", "female"));
        v.add(voice("aifan", "艾凡(情感女声)", "zh-CN", "female"));
        v.add(voice("ainan", "艾楠(广告男声)", "zh-CN", "male"));
        v.add(voice("aihao", "艾浩(资讯男声)", "zh-CN", "male"));
        v.add(voice("aiming", "艾茗(诙谐男声)", "zh-CN", "male"));
        v.add(voice("aixiao", "艾笑(资讯女声)", "zh-CN", "female"));
        v.add(voice("aichu", "艾厨(舌尖男声)", "zh-CN", "male"));
        v.add(voice("aiqian", "艾倩(资讯女声)", "zh-CN", "female"));
        v.add(voice("aishu", "艾树(资讯男声)", "zh-CN", "male"));
        v.add(voice("airu", "艾茹(新闻女声)", "zh-CN", "female"));
        // ========== 直播场景 ==========
        v.add(voice("xiaoxian", "小仙(亲切女声)", "zh-CN", "female"));
        v.add(voice("maoxiaomei", "猫小美(活力女声)", "zh-CN", "female"));
        v.add(voice("aifei", "艾飞(激昂解说)", "zh-CN", "male"));
        v.add(voice("yaqun", "亚群(卖场广播)", "zh-CN", "female"));
        v.add(voice("qiaowei", "巧薇(卖场广播)", "zh-CN", "female"));
        v.add(voice("ailun", "艾伦(悬疑解说)", "zh-CN", "male"));
        v.add(voice("laotie", "老铁(东北老铁)", "zh-CN", "male"));
        v.add(voice("laomei", "老妹(吆喝女声)", "zh-CN", "female"));
        // ========== 童声场景 ==========
        v.add(voice("sitong", "思彤(儿童音)", "zh-CN", "child"));
        v.add(voice("xiaobei", "小北(萝莉女声)", "zh-CN", "female"));
        v.add(voice("aitong", "艾彤(儿童音)", "zh-CN", "child"));
        v.add(voice("aiwei", "艾薇(萝莉女声)", "zh-CN", "female"));
        v.add(voice("aibao", "艾宝(萝莉女声)", "zh-CN", "female"));
        v.add(voice("jielidou", "杰力豆(治愈童声)", "zh-CN", "child"));
        // ========== 英文场景 ==========
        v.add(voice("harry", "Harry(英音男声)", "en-GB", "male"));
        v.add(voice("abby", "Abby(美音女声)", "en-US", "female"));
        v.add(voice("andy", "Andy(美音男声)", "en-US", "male"));
        v.add(voice("eric", "Eric(英音男声)", "en-GB", "male"));
        v.add(voice("emily", "Emily(英音女声)", "en-GB", "female"));
        v.add(voice("luna", "Luna(英音女声)", "en-GB", "female"));
        v.add(voice("luca", "Luca(英音男声)", "en-GB", "male"));
        v.add(voice("wendy", "Wendy(英音女声)", "en-GB", "female"));
        v.add(voice("william", "William(英音男声)", "en-GB", "male"));
        v.add(voice("olivia", "Olivia(英音女声)", "en-GB", "female"));
        v.add(voice("lydia", "Lydia(英中双语女声)", "en-US", "female"));
        v.add(voice("annie", "Annie(美语女声)", "en-US", "female"));
        v.add(voice("ava", "ava(美语女生)", "en-US", "female"));
        v.add(voice("becca", "Becca(美语客服女声)", "en-US", "female"));
        v.add(voice("betty", "betty(美式英文女声)", "en-US", "female"));
        v.add(voice("beth", "beth(美式英文女声)", "en-US", "female"));
        v.add(voice("cindy", "cindy(美式英文女声)", "en-US", "female"));
        v.add(voice("donna", "donna(美式英文女声)", "en-US", "female"));
        v.add(voice("eva", "eva(美式英文女声)", "en-US", "female"));
        v.add(voice("brian", "brian(美式英文男声)", "en-US", "male"));
        v.add(voice("david", "david(美式英文男声)", "en-US", "male"));
        // ========== 英文及英中文混合场景 ==========
        v.add(voice("abby_ecmix", "abby_ecmix(美式英文女声)", "en-US", "female"));
        v.add(voice("annie_ecmix", "annie_ecmix(美式英文女声)", "en-US", "female"));
        v.add(voice("andy_ecmix", "andy_ecmix(美式英文男声)", "en-US", "male"));
        v.add(voice("ava_ecmix", "ava_ecmix(美式英文女声)", "en-US", "female"));
        v.add(voice("betty_ecmix", "betty_ecmix(美式英文女声)", "en-US", "female"));
        v.add(voice("beth_ecmix", "beth_ecmix(美式英文女声)", "en-US", "female"));
        v.add(voice("brian_ecmix", "brian_ecmix(美式英文男声)", "en-US", "male"));
        v.add(voice("cindy_ecmix", "cindy_ecmix(美式英文女声)", "en-US", "female"));
        v.add(voice("cally_ecmix", "cally_ecmix(美式英文女声)", "en-US", "female"));
        v.add(voice("donna_ecmix", "donna_ecmix(美式英文女声)", "en-US", "female"));
        v.add(voice("david_ecmix", "david_ecmix(美式英文男声)", "en-US", "male"));
        v.add(voice("eva_ecmix", "eva_ecmix(美式英文女声)", "en-US", "female"));
        // ========== 方言场景 ==========
        v.add(voice("shanshan", "姗姗(粤语女声)", "yue", "female"));
        v.add(voice("jiajia", "佳佳(粤语女声)", "yue", "female"));
        v.add(voice("taozi", "桃子(粤语女声)", "yue", "female"));
        v.add(voice("kelly", "Kelly(香港粤语女声)", "yue", "female"));
        v.add(voice("chuangirl", "小玥(四川话女声)", "zh-CN", "female"));
        v.add(voice("qingqing", "青青(台湾话女声)", "zh-CN", "female"));
        v.add(voice("cuijie", "翠姐(东北话女声)", "zh-CN", "female"));
        v.add(voice("xiaoze", "小泽(湖南重口音男声)", "zh-CN", "male"));
        v.add(voice("dahu", "大虎(东北话男声)", "zh-CN", "male"));
        v.add(voice("aikan", "艾侃(天津话男声)", "zh-CN", "male"));
        v.add(voice("zhiqing", "知青(台湾话女生)", "zh-CN", "female"));
        // ========== 多语种场景 ==========
        v.add(voice("tomoka", "智香(日语女声)", "ja-JP", "female"));
        v.add(voice("tomoya", "智也(日语男声)", "ja-JP", "male"));
        v.add(voice("indah", "Indah(印尼语女声)", "id-ID", "female"));
        v.add(voice("farah", "Farah(马来语女声)", "ms-MY", "female"));
        v.add(voice("tala", "Tala(菲律宾语女声)", "fil-PH", "female"));
        v.add(voice("tien", "Tien(越南语女声)", "vi-VN", "female"));
        v.add(voice("Kyong", "Kyong(韩语女声)", "ko-KR", "female"));
        v.add(voice("masha", "masha(俄语女声)", "ru-RU", "female"));
        v.add(voice("camila", "camila(西班牙语女声)", "es-ES", "female"));
        v.add(voice("perla", "perla(意大利语女声)", "it-IT", "female"));
        v.add(voice("clara", "clara(法语女声)", "fr-FR", "female"));
        v.add(voice("hanna", "hanna(德语女声)", "de-DE", "female"));
        v.add(voice("waan", "waan(泰语女声)", "th-TH", "female"));
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
    public byte[] synthesize(String configJson, String voiceId, String text, String emotion) {
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

            StringBuilder urlBuilder = new StringBuilder(TOKEN_URL)
                    .append("?appkey=").append(appKey)
                    .append("&token=").append(token)
                    .append("&text=").append(URLEncoder.encode(text, "UTF-8"))
                    .append("&format=mp3")
                    .append("&voice=").append(voiceId)
                    .append("&sample_rate=16000")
                    .append("&volume=50")
                    .append("&speech_rate=0")
                    .append("&pitch_rate=0");
            if (emotion != null && !emotion.isEmpty()) {
                urlBuilder.append("&emotion=").append(URLEncoder.encode(emotion, "UTF-8"));
            }

            byte[] audioBytes = httpGet(urlBuilder.toString());
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