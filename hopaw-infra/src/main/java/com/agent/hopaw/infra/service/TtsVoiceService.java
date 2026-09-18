package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.TtsVoiceMapper;
import com.agent.hopaw.infra.model.dto.TtsVoice;
import com.agent.hopaw.infra.model.entity.TtsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 渠道音色服务：每个 TTS 配置（渠道）的音色独立存储于 tts_voice 表。
 * 添加配置时复制厂商实现的默认音色；之后可按渠道增删改；使用时按渠道查询。
 */
@Service
public class TtsVoiceService {

    private static final Logger log = LoggerFactory.getLogger(TtsVoiceService.class);

    private final TtsVoiceMapper ttsVoiceMapper;
    private final TtsServiceFactory ttsServiceFactory;
    private final TtsConfigService ttsConfigService;

    public TtsVoiceService(TtsVoiceMapper ttsVoiceMapper,
                           TtsServiceFactory ttsServiceFactory,
                           TtsConfigService ttsConfigService) {
        this.ttsVoiceMapper = ttsVoiceMapper;
        this.ttsServiceFactory = ttsServiceFactory;
        this.ttsConfigService = ttsConfigService;
    }

    /** 查询某渠道的音色列表（无数据返回空列表） */
    public List<TtsVoice> listByConfigId(Long configId) {
        List<TtsVoice> voices = ttsVoiceMapper.findByConfigId(configId);
        return voices == null ? List.of() : voices;
    }

    /** 统计某渠道的音色数量 */
    public int countByConfigId(Long configId) {
        return ttsVoiceMapper.countByConfigId(configId);
    }

    /** 删除某渠道的全部音色（删除配置时清理） */
    public void clearByConfigId(Long configId) {
        ttsVoiceMapper.deleteByConfigId(configId);
    }

    /**
     * 全量替换某渠道的音色：先删除后插入，返回最终条数。
     * 仅更新传入字段，服务端补齐 configId，id 忽略（不依赖前端回传的主键）。
     */
    @Transactional
    public int saveAll(Long configId, List<TtsVoice> voices) {
        ttsVoiceMapper.deleteByConfigId(configId);
        if (voices == null || voices.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (TtsVoice voice : voices) {
            if (voice.getVoiceId() == null || voice.getVoiceId().isBlank()) {
                continue;
            }
            TtsVoice copy = new TtsVoice();
            copy.setConfigId(configId);
            copy.setVoiceId(voice.getVoiceId().trim());
            copy.setVoiceName(voice.getVoiceName());
            copy.setLanguage(voice.getLanguage());
            copy.setGender(voice.getGender());
            copy.setDescription(voice.getDescription());
            copy.setEmotions(voice.getEmotions());
            ttsVoiceMapper.insert(copy);
            count++;
        }
        return count;
    }

    /**
     * 将厂商实现的默认音色复制入库到指定渠道。
     * 传入解密后的 configJson，供厂商实现读取（如 Piper 需 baseUrl 查询远程 /voices）。
     */
    @Transactional
    public int initDefaultVoices(Long configId, String vendorCode) {
        ITtsService service = ttsServiceFactory.getService(vendorCode);
        if (service == null) {
            log.warn("TTS 厂商未注册，无法复制默认音色: {}", vendorCode);
            return 0;
        }
        // 查询解密后的配置，为厂商实现提供 baseUrl 等参数
        TtsConfig config = ttsConfigService.findById(configId);
        String configJson = (config == null) ? "" : config.getConfigJson();
        List<TtsVoice> defaultVoices = service.listVoices(configJson);
        if (defaultVoices == null || defaultVoices.isEmpty()) {
            log.info("TTS 厂商 {} 无默认音色，跳过复制", vendorCode);
            return 0;
        }
        int count = saveAll(configId, defaultVoices);
        log.info("已为 TTS 配置 id={} 复制默认音色 {} 条", configId, count);
        return count;
    }
}
