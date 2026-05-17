package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.SysConfig;

import java.util.List;

public interface ISysConfigService {
    List<SysConfig> getAll();
    SysConfig getByKey(String key);
    String getValueByKey(String key, String defaultValue);
    int save(SysConfig sysConfig);
    int update(SysConfig sysConfig);
    int deleteById(Long id);
    int deleteByKey(String key);
}
