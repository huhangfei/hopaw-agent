package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.TtsConfigMapper;
import com.agent.hopaw.infra.model.entity.TtsConfig;
import com.agent.hopaw.infra.service.ITtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * TTS 聚合服务，负责根据配置查找合适的 TTS 实现并合成语音。
 */
@Service
public class TtsService {

    private static final Logger logger = LoggerFactory.getLogger(TtsService.class);

    private final TtsConfigMapper ttsConfigMapper;
    private final TtsServiceFactory ttsServiceFactory;

    public TtsService(TtsConfigMapper ttsConfigMapper, TtsServiceFactory ttsServiceFactory) {
        this.ttsConfigMapper = ttsConfigMapper;
        this.ttsServiceFactory = ttsServiceFactory;
    }

    /**
     * 合成语音并返回 base64 编码的音频数据。
     * @return base64 字符串，如果 TTS 未启用或合成失败返回 null
     */
    public String synthesizeToBase64(String text) {
        try {
            TtsConfig config = ttsConfigMapper.findEnabledByUserId("admin");
            if (config == null || config.getEnabled() == null || config.getEnabled() != 1) {
                return null;
            }
            ITtsService service = ttsServiceFactory.getService(config.getVendorCode());
            if (service == null) {
                logger.warn("TTS 厂商未注册: {}", config.getVendorCode());
                return null;
            }
            String voiceId = config.getDefaultVoiceId();
            if (voiceId == null || voiceId.isEmpty()) {
                logger.warn("TTS 未配置默认音色");
                return null;
            }
            byte[] audio = service.synthesize(config.getConfigJson(), voiceId, text);
            if (audio == null || audio.length == 0) {
                return null;
            }
            return Base64.getEncoder().encodeToString(audio);
        } catch (Exception e) {
            logger.error("TTS 合成失败: {}", e.getMessage(), e);
            return null;
        }
    }
}