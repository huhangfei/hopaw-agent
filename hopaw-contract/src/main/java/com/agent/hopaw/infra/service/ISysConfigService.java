package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.SysConfig;

import java.util.List;

public interface ISysConfigService {
    List<SysConfig> getAll();
    SysConfig getByKey(String key);
    List<SysConfig> getByKeys(List<String> keys);
    String getValueByKey(String key, String defaultValue);
    /** 保存配置（不加密） */
    int insert(SysConfig sysConfig);
    /** 保存配置（不加密） */
    int update(SysConfig sysConfig);
    /**
     * 保存配置
     * @param encrypt true=加密存储（is_encrypted=1），false=明文存储（is_encrypted=0）
     */
    int insert(SysConfig sysConfig, boolean encrypt);
    /**
     * 更新配置
     * @param encrypt true=加密存储（is_encrypted=1），false=明文存储（is_encrypted=0）
     */
    int update(SysConfig sysConfig, boolean encrypt);
    int deleteById(Long id);
    int deleteByKey(String key);
}
