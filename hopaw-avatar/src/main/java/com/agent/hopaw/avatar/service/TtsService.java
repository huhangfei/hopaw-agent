package com.agent.hopaw.avatar.service;

import com.agent.hopaw.avatar.entity.AgentAvatarConfig;
import com.agent.hopaw.avatar.mapper.AvatarConfigMapper;
import com.agent.hopaw.infra.mapper.TtsConfigMapper;
import com.agent.hopaw.infra.model.entity.TtsConfig;
import com.agent.hopaw.infra.service.ITtsService;
import com.agent.hopaw.infra.service.TtsServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * 虚拟人 TTS 聚合服务，负责根据 agent 配置查找 TTS 音色、根据全局配置获取厂商凭证，合成语音。
 */
@Service
public class TtsService {

    private static final Logger logger = LoggerFactory.getLogger(TtsService.class);

    private final TtsConfigMapper ttsConfigMapper;
    private final AvatarConfigMapper avatarConfigMapper;
    private final TtsServiceFactory ttsServiceFactory;

    public TtsService(TtsConfigMapper ttsConfigMapper,
                      AvatarConfigMapper avatarConfigMapper,
                      TtsServiceFactory ttsServiceFactory) {
        this.ttsConfigMapper = ttsConfigMapper;
        this.avatarConfigMapper = avatarConfigMapper;
        this.ttsServiceFactory = ttsServiceFactory;
    }

    /**
     * 合成语音并返回 base64 编码的音频数据。
     * @param userId 用户 ID
     * @param agentId 智能体 ID
     * @param text 文本内容
     * @return base64 字符串，如果 TTS 未启用或合成失败返回 null
     */
    public String synthesizeToBase64(String userId, Long agentId, String text) {
        try {
            // 1. 查询 agent 的 TTS 配置
            AgentAvatarConfig agentConfig = avatarConfigMapper.findByUserAndAgent(userId, agentId);
            if (agentConfig == null || !Boolean.TRUE.equals(agentConfig.getTtsEnabled())) {
                return null;
            }
            String vendorCode = agentConfig.getTtsVendorCode();
            String voiceId = agentConfig.getTtsVoiceId();
            if (vendorCode == null || vendorCode.isEmpty()) {
                logger.warn("TTS: agent {} 未配置厂商编号", agentId);
                return null;
            }
            if (voiceId == null || voiceId.isEmpty()) {
                logger.warn("TTS: agent {} 未配置音色", agentId);
                return null;
            }

            // 2. 查询全局厂商凭证配置
            TtsConfig vendorConfig = ttsConfigMapper.findByVendorCode(vendorCode);
            if (vendorConfig == null || vendorConfig.getEnabled() == null || vendorConfig.getEnabled() != 1) {
                logger.warn("TTS: 厂商 {} 未启用或未配置", vendorCode);
                return null;
            }

            // 3. 获取厂商实现并合成
            ITtsService service = ttsServiceFactory.getService(vendorCode);
            if (service == null) {
                logger.warn("TTS 厂商未注册: {}", vendorCode);
                return null;
            }
            byte[] audio = service.synthesize(vendorConfig.getConfigJson(), voiceId, text);
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