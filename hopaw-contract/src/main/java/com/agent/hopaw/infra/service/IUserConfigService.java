package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.UserConfig;

import java.util.List;

public interface IUserConfigService {
    List<UserConfig> getAllByUserId(String userId);
    UserConfig getByUserIdAndKey(String userId, String key);
    List<UserConfig> getByUserIdAndKeys(String userId, List<String> keys);
    String getValueByKey(String userId, String key, String defaultValue);
    int insert(UserConfig userConfig);
    int update(UserConfig userConfig);
    int deleteById(Long id);
    int deleteByUserIdAndKey(String userId, String key);
}
