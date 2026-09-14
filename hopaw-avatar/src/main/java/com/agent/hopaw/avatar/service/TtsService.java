package com.agent.hopaw.avatar.service;

import com.agent.hopaw.avatar.entity.AgentAvatarConfig;
import com.agent.hopaw.avatar.mapper.AvatarConfigMapper;
import com.agent.hopaw.infra.model.entity.TtsConfig;
import com.agent.hopaw.infra.service.ITtsService;
import com.agent.hopaw.infra.service.TtsConfigService;
import com.agent.hopaw.infra.service.TtsServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 虚拟人 TTS 聚合服务，负责根据 agent 配置查找 TTS 音色、根据全局配置获取厂商凭证，合成语音。
 */
@Service
public class TtsService {

    private static final Logger logger = LoggerFactory.getLogger(TtsService.class);

    private final TtsConfigService ttsConfigService;
    private final AvatarConfigMapper avatarConfigMapper;
    private final TtsServiceFactory ttsServiceFactory;

    public TtsService(TtsConfigService ttsConfigService,
                      AvatarConfigMapper avatarConfigMapper,
                      TtsServiceFactory ttsServiceFactory) {
        this.ttsConfigService = ttsConfigService;
        this.avatarConfigMapper = avatarConfigMapper;
        this.ttsServiceFactory = ttsServiceFactory;
    }

    /**
     * 按断句标点切分文本后逐段合成，每段合成完成即通过回调返回，供调用方顺序推送到前端。
     * 整段文本一次合成耗时过长，分段后首段可更早送达播放。
     * @param userId 用户 ID
     * @param agentId 智能体 ID
     * @param text 文本内容
     * @param emotion 情感类型（可为 null）
     * @param onSegment 分段回调：(base64 音频, 分段文本)；TTS 未启用或配置缺失时不回调
     */
    public void synthesizeSegmented(String userId, Long agentId, String text, String emotion,
                                    BiConsumer<String, String> onSegment) {
        try {
            // 1. 查询 agent 的 TTS 配置
            AgentAvatarConfig agentConfig = avatarConfigMapper.findByUserAndAgent(userId, agentId);
            if (agentConfig == null || !Boolean.TRUE.equals(agentConfig.getTtsEnabled())) {
                return;
            }
            Long ttsConfigId = agentConfig.getTtsConfigId();
            String voiceId = agentConfig.getTtsVoiceId();
            if (ttsConfigId == null) {
                logger.warn("TTS: agent {} 未配置 TTS 配置主键", agentId);
                return;
            }
            if (voiceId == null || voiceId.isEmpty()) {
                logger.warn("TTS: agent {} 未配置音色", agentId);
                return;
            }

            // 2. 查询全局 TTS 厂商配置（TtsConfigService 返回已解密的 configJson）
            TtsConfig ttsConfig = ttsConfigService.findById(ttsConfigId);
            if (ttsConfig == null || ttsConfig.getEnabled() == null || ttsConfig.getEnabled() != 1) {
                logger.warn("TTS: 配置 id={} 未启用或不存在", ttsConfigId);
                return;
            }
            String vendorCode = ttsConfig.getVendorCode();

            // 3. 获取厂商实现
            ITtsService service = ttsServiceFactory.getService(vendorCode);
            if (service == null) {
                logger.warn("TTS 厂商未注册: {}", vendorCode);
                return;
            }

            // 4. 按断句标点切分后逐段合成并回调，单段失败不影响后续段
            List<String> segments = splitIntoSegments(text);
            for (String segment : segments) {
                try {
                    byte[] audio = service.synthesize(ttsConfig.getConfigJson(), voiceId, segment, emotion);
                    if (audio != null && audio.length > 0) {
                        onSegment.accept(Base64.getEncoder().encodeToString(audio), segment);
                    }
                } catch (Exception e) {
                    logger.warn("TTS 分段合成失败（跳过该段）: segment={} err={}", segment, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("TTS 合成失败: {}", e.getMessage(), e);
        }
    }

    /** 单段最小字符数：过短的分段向后合并，避免产生大量极小的合成请求 */
    private static final int MIN_SEGMENT_CHARS = 10;
    /** 单段最大字符数：无强断句时超长段在最近的次级标点（逗号等）处断开，避免单段合成过久 */
    private static final int MAX_SEGMENT_CHARS = 150;

    /**
     * 按断句标点切分文本：强断句符（。！？!?；;…换行）后切分，英文句点仅在后接空白时视为断句（避免小数点误切）；
     * 超长无断句的段退化为在次级标点（，,、：:）或硬切处断开。
     */
    static List<String> splitIntoSegments(String text) {
        List<String> segments = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return segments;
        }
        int len = text.length();
        StringBuilder buf = new StringBuilder();
        int lastSecondaryIdx = -1;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            buf.append(c);
            if (isSecondaryPunctuation(c)) {
                lastSecondaryIdx = buf.length() - 1;
            }
            if (isSentenceEnd(text, i)) {
                addSegment(segments, buf);
                lastSecondaryIdx = -1;
            } else if (buf.length() >= MAX_SEGMENT_CHARS) {
                if (lastSecondaryIdx > 0) {
                    String tail = buf.substring(lastSecondaryIdx + 1);
                    buf.setLength(lastSecondaryIdx + 1);
                    addSegment(segments, buf);
                    buf.append(tail);
                    lastSecondaryIdx = -1;
                } else {
                    addSegment(segments, buf);
                    lastSecondaryIdx = -1;
                }
            }
        }
        addSegment(segments, buf);
        return mergeShortSegments(segments);
    }

    private static boolean isSentenceEnd(String text, int i) {
        char c = text.charAt(i);
        if (c == '。' || c == '！' || c == '？' || c == '；' || c == '…' || c == '\n' || c == '\r'
                || c == '!' || c == '?' || c == ';') {
            return true;
        }
        if (c == '.') {
            return i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1));
        }
        return false;
    }

    private static boolean isSecondaryPunctuation(char c) {
        return c == '，' || c == ',' || c == '、' || c == '：' || c == ':';
    }

    private static void addSegment(List<String> segments, StringBuilder buf) {
        String s = buf.toString().trim();
        if (!s.isEmpty()) {
            segments.add(s);
        }
        buf.setLength(0);
    }

    /** 累计不足最小长度的相邻分段向后合并，最后一段过短并入前一段 */
    private static List<String> mergeShortSegments(List<String> segments) {
        List<String> merged = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        for (String segment : segments) {
            pending.append(segment);
            if (pending.length() >= MIN_SEGMENT_CHARS) {
                merged.add(pending.toString());
                pending.setLength(0);
            }
        }
        if (pending.length() > 0) {
            if (!merged.isEmpty() && pending.length() < MIN_SEGMENT_CHARS) {
                merged.set(merged.size() - 1, merged.get(merged.size() - 1) + pending);
            } else {
                merged.add(pending.toString());
            }
        }
        return merged;
    }
}