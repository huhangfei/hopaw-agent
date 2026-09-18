package com.agent.hopaw.infra.constant;

import java.util.ArrayList;
import java.util.List;

/**
 * TTS 语音合成标准情感枚举。
 * 各厂商实现需将此枚举映射为自家 API 所需的格式。
 */
public enum TtsEmotionEnum {

    NEUTRAL("neutral", "平静"),
    HAPPY("happy", "开心"),
    SAD("sad", "悲伤"),
    ANGRY("angry", "愤怒"),
    FEAR("fear", "恐惧"),
    SURPRISE("surprise", "惊讶"),
    HATE("hate", "厌恶"),
    AROUSAL("arousal", "激动"),
    DISGUST("disgust", "反感"),
    JEALOUSY("jealousy", "嫉妒"),
    EMBARRASSED("embarrassed", "尴尬"),
    FRUSTRATED("frustrated", "沮丧"),
    AFFECTIONATE("affectionate", "深情"),
    GENTLE("gentle", "温柔"),
    SERIOUS("serious", "严肃"),
    EXCITED("excited", "兴奋"),
    LIVELY("lively", "活泼"),
    NEWSCAST("newscast", "新闻播报"),
    CUSTOMER_SERVICE("customer-service", "客服"),
    STORY("story", "故事讲述"),
    LIVING("living", "生活化");

    private final String code;
    private final String name;

    TtsEmotionEnum(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    /**
     * 从 code 反查枚举，未匹配返回 null
     */
    public static TtsEmotionEnum fromCode(String code) {
        if (code == null) return null;
        for (TtsEmotionEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 将逗号分隔的情感代码字符串解析为枚举列表，未知 code 跳过
     */
    public static List<TtsEmotionEnum> fromCodes(String codes) {
        List<TtsEmotionEnum> result = new ArrayList<>();
        if (codes == null || codes.isBlank()) return result;
        for (String s : codes.split(",")) {
            TtsEmotionEnum e = fromCode(s.trim());
            if (e != null) {
                result.add(e);
            }
        }
        return result;
    }

    /**
     * 将枚举列表转为 code 逗号分隔字符串
     */
    public static String toCodes(List<TtsEmotionEnum> emotions) {
        if (emotions == null || emotions.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < emotions.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(emotions.get(i).code);
        }
        return sb.toString();
    }
}
