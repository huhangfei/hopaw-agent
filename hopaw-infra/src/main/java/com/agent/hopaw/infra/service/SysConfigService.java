package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.SysConfigMapper;
import com.agent.hopaw.infra.model.entity.SysConfig;
import com.agent.hopaw.infra.util.AesEncryptionUtil;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class SysConfigService implements ISysConfigService {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "mail_password",
            "qianfan_web_search_api_keys"
    );

    private final SysConfigMapper sysConfigMapper;

    public SysConfigService(SysConfigMapper sysConfigMapper) {
        this.sysConfigMapper = sysConfigMapper;
    }

    public List<SysConfig> getAll() {
        List<SysConfig> configs = sysConfigMapper.findAll();
        for (SysConfig config : configs) {
            if (SENSITIVE_KEYS.contains(config.getConfigKey())) {
                config.setConfigValue(AesEncryptionUtil.decrypt(config.getConfigValue()));
            }
        }
        return configs;
    }

    public SysConfig getByKey(String key) {
        SysConfig config = sysConfigMapper.findByKey(key);
        if (config != null && SENSITIVE_KEYS.contains(key)) {
            config.setConfigValue(AesEncryptionUtil.decrypt(config.getConfigValue()));
        }
        return config;
    }

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

    public int save(SysConfig sysConfig) {
        String plainValue = sysConfig.getConfigValue();
        if (SENSITIVE_KEYS.contains(sysConfig.getConfigKey())) {
            sysConfig.setConfigValue(AesEncryptionUtil.encrypt(plainValue));
        }
        int result = sysConfigMapper.insert(sysConfig);
        sysConfig.setConfigValue(plainValue);
        return result;
    }

    public int update(SysConfig sysConfig) {
        String plainValue = sysConfig.getConfigValue();
        if (SENSITIVE_KEYS.contains(sysConfig.getConfigKey())) {
            sysConfig.setConfigValue(AesEncryptionUtil.encrypt(plainValue));
        }
        int result = sysConfigMapper.update(sysConfig);
        sysConfig.setConfigValue(plainValue);
        return result;
    }

    public int deleteById(Long id) {
        return sysConfigMapper.deleteById(id);
    }

    public int deleteByKey(String key) {
        return sysConfigMapper.deleteByKey(key);
    }
}
