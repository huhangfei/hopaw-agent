package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.TtsConfigMapper;
import com.agent.hopaw.infra.model.entity.TtsConfig;
import com.agent.hopaw.infra.util.AesEncryptionUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TTS 配置服务：configJson 含厂商 apiKey 等敏感凭证，
 * 写入数据库前使用 AesEncryptionUtil 加密，读取后解密返回明文。
 * <p>历史数据兼容：数据库中的明文 configJson 读取时原样返回，下次保存时自动加密。</p>
 */
@Service
public class TtsConfigService {

    private static final Logger log = LoggerFactory.getLogger(TtsConfigService.class);

    private final TtsConfigMapper ttsConfigMapper;

    public TtsConfigService(TtsConfigMapper ttsConfigMapper) {
        this.ttsConfigMapper = ttsConfigMapper;
    }

    /** 查询所有 TTS 配置（configJson 已解密） */
    public List<TtsConfig> findAll() {
        return decryptAll(ttsConfigMapper.findAll());
    }

    /** 查询所有已启用的 TTS 配置（configJson 已解密） */
    public List<TtsConfig> findAllEnabled() {
        return decryptAll(ttsConfigMapper.findAllEnabled());
    }

    /** 按用户查询已启用的 TTS 配置（configJson 已解密） */
    public TtsConfig findEnabledByUserId(String userId) {
        return decrypt(ttsConfigMapper.findEnabledByUserId(userId));
    }

    /** 按主键查询 TTS 配置（configJson 已解密） */
    public TtsConfig findById(Long id) {
        return decrypt(ttsConfigMapper.findById(id));
    }

    /** 保存 TTS 配置（新增或更新），configJson 加密后写入 */
    public void save(TtsConfig config) {
        encrypt(config);
        if (config.getId() != null) {
            ttsConfigMapper.update(config);
        } else {
            ttsConfigMapper.insert(config);
        }
    }

    /** 删除 TTS 配置 */
    public void deleteById(Long id) {
        ttsConfigMapper.deleteById(id);
    }

    /** 加密 configJson：仅处理明文，已是 {AES} 密文则保持原样 */
    private void encrypt(TtsConfig config) {
        if (config == null) {
            return;
        }
        String json = config.getConfigJson();
        if (json == null || json.isBlank() || AesEncryptionUtil.isEncrypted(json)) {
            return;
        }
        config.setConfigJson(AesEncryptionUtil.encrypt(json));
    }

    /** 解密 configJson：仅处理 {AES} 密文；解密失败保留密文并记录日志，不阻塞读取 */
    private TtsConfig decrypt(TtsConfig config) {
        if (config == null) {
            return null;
        }
        String json = config.getConfigJson();
        if (json != null && AesEncryptionUtil.isEncrypted(json)) {
            try {
                config.setConfigJson(AesEncryptionUtil.decrypt(json));
            } catch (Exception e) {
                log.error("TTS 配置 id={} 的 configJson 解密失败", config.getId(), e);
            }
        }
        return config;
    }

    private List<TtsConfig> decryptAll(List<TtsConfig> configs) {
        if (configs == null) {
            return List.of();
        }
        configs.forEach(this::decrypt);
        return configs;
    }
}
