package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.SysConfigMapper;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.util.AesEncryptionUtil;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SysConfigService implements ISysConfigService {

    private static final Set<String> SENSITIVE_KEYS = new HashSet<>();



    private final SysConfigMapper sysConfigMapper;

    public SysConfigService(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }
    @Override
    public void setSensitiveKeys(String... keys){
        SENSITIVE_KEYS.addAll(Set.of(keys));
    }
    @Override
    public List<SysConfig> getAll() {
        List<SysConfig> configs = sysConfigMapper.findAll();
        for (SysConfig config : configs) {
            if (SENSITIVE_KEYS.contains(config.getConfigKey())) {
                config.setConfigValue(AesEncryptionUtil.decrypt(config.getConfigValue()));
            }
        }
        return configs;
    }

    @Override
    public SysConfig getByKey(String key) {
        SysConfig config = sysConfigMapper.findByKey(key);
        if (config != null && SENSITIVE_KEYS.contains(key)) {
            config.setConfigValue(AesEncryptionUtil.decrypt(config.getConfigValue()));
        }
        return config;
    }

    @Override
    public List<SysConfig> getByKeys(List<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        List<SysConfig> configs = sysConfigMapper.findByKeys(keys);
        for (SysConfig config : configs) {
            if (SENSITIVE_KEYS.contains(config.getConfigKey())) {
                config.setConfigValue(AesEncryptionUtil.decrypt(config.getConfigValue()));
            }
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
    public int save(SysConfig sysConfig) {
        String plainValue = sysConfig.getConfigValue();
        if (SENSITIVE_KEYS.contains(sysConfig.getConfigKey())) {
            sysConfig.setConfigValue(AesEncryptionUtil.encrypt(plainValue));
        }
        int result = sysConfigMapper.insert(sysConfig);
        sysConfig.setConfigValue(plainValue);
        return result;
    }

    @Override
    public int update(SysConfig sysConfig) {
        String plainValue = sysConfig.getConfigValue();
        if (SENSITIVE_KEYS.contains(sysConfig.getConfigKey())) {
            sysConfig.setConfigValue(AesEncryptionUtil.encrypt(plainValue));
        }
        int result = sysConfigMapper.update(sysConfig);
        sysConfig.setConfigValue(plainValue);
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
}
