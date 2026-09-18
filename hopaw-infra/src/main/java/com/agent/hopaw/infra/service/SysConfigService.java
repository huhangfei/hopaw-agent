package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.event.ConfigChangeEvent;
import com.agent.hopaw.infra.mapper.SysConfigMapper;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.util.AesEncryptionUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;


@Service
public class SysConfigService implements ISysConfigService {

    private final SysConfigMapper sysConfigMapper;
    private final ApplicationEventPublisher eventPublisher;

    public SysConfigService(SysConfigMapper sysConfigMapper, ApplicationEventPublisher eventPublisher) {
        this.sysConfigMapper = sysConfigMapper;
        this.eventPublisher = eventPublisher;
    }
    @Override
    public List<SysConfig> getAll() {
        List<SysConfig> configs = sysConfigMapper.findAll();
        for (SysConfig config : configs) {
            decryptIfNeeded(config);
        }
        return configs;
    }

    @Override
    public SysConfig getByKey(String key) {
        SysConfig config = sysConfigMapper.findByKey(key);
        decryptIfNeeded(config);
        return config;
    }

    @Override
    public List<SysConfig> getByKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<SysConfig> configs = sysConfigMapper.findByKeys(keys);
        for (SysConfig config : configs) {
            decryptIfNeeded(config);
        }
        return configs;
    }

    @Override
    public String getValueByKey(String key, String defaultValue) {
        var config = getByKey(key);
        if(config==null){
            return defaultValue;
        }
        if(config.getConfigValue()==null || config.getConfigValue().isEmpty()){
            return defaultValue;
        }
        return config.getConfigValue();
    }

    @Override
    public int insert(SysConfig sysConfig) {
        return insert(sysConfig, false);
    }

    @Override
    public int update(SysConfig sysConfig) {
        return update(sysConfig, false);
    }

    @Override
    public int insert(SysConfig sysConfig, boolean encrypt) {
        String plainValue = sysConfig.getConfigValue();
        sysConfig.setIsEncrypted(encrypt ? 1 : 0);
        if (encrypt) {
            sysConfig.setConfigValue(AesEncryptionUtil.encrypt(plainValue));
        }
        int result = sysConfigMapper.insert(sysConfig);
        sysConfig.setConfigValue(plainValue);
        eventPublisher.publishEvent(new ConfigChangeEvent(Set.of(sysConfig.getConfigKey())));
        return result;
    }

    @Override
    public int update(SysConfig sysConfig, boolean encrypt) {
        String plainValue = sysConfig.getConfigValue();
        sysConfig.setIsEncrypted(encrypt ? 1 : 0);
        if (encrypt) {
            sysConfig.setConfigValue(AesEncryptionUtil.encrypt(plainValue));
        }
        int result = sysConfigMapper.update(sysConfig);
        sysConfig.setConfigValue(plainValue);
        eventPublisher.publishEvent(new ConfigChangeEvent(Set.of(sysConfig.getConfigKey())));
        return result;
    }

    @Override
    public int deleteById(Long id) {
        return sysConfigMapper.deleteById(id);
    }
    @Override
    public int deleteByKey(String key) {
        return sysConfigMapper.deleteByKey(key);
    }

    /**
     * 按记录自身的 is_encrypted 标记决定是否解密（对未加密记录原样返回）
     */
    private void decryptIfNeeded(SysConfig config) {
        if (config != null && config.getIsEncrypted() != null && config.getIsEncrypted() == 1) {
            config.setConfigValue(AesEncryptionUtil.decrypt(config.getConfigValue()));
        }
    }
}
