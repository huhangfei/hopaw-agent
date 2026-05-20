package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.SysConfig;

import java.util.List;

public interface ISysConfigService {
    void setSensitiveKeys(String... keys);
    List<SysConfig> getAll();
    SysConfig getByKey(String key);
    List<SysConfig> getByKeys(List<String> keys);
    String getValueByKey(String key, String defaultValue);
    int save(SysConfig sysConfig);
    int update(SysConfig sysConfig);
    int deleteById(Long id);
    int deleteByKey(String key);
}
