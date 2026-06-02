package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.event.UserConfigChangeEvent;
import com.agent.hopaw.infra.mapper.UserConfigMapper;
import com.agent.hopaw.infra.model.entity.UserConfig;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class UserConfigService implements IUserConfigService {

    private final UserConfigMapper userConfigMapper;
    private final ApplicationEventPublisher eventPublisher;

    public UserConfigService(UserConfigMapper userConfigMapper, ApplicationEventPublisher eventPublisher) {
        this.userConfigMapper = userConfigMapper;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<UserConfig> getAllByUserId(String userId) {
        return userConfigMapper.findByUserId(userId);
    }

    @Override
    public UserConfig getByUserIdAndKey(String userId, String key) {
        return userConfigMapper.findByUserIdAndKey(userId, key);
    }

    @Override
    public List<UserConfig> getByUserIdAndKeys(String userId, List<String> keys) {
        if (userId == null || keys == null || keys.isEmpty()) {
            return List.of();
        }
        return userConfigMapper.findByUserIdAndKeys(userId, keys);
    }

    @Override
    public String getValueByKey(String userId, String key, String defaultValue) {
        UserConfig config = getByUserIdAndKey(userId, key);
        if (config == null) {
            return defaultValue;
        }
        String value = config.getConfigValue();
        if (value == null || value.isEmpty()) {
            return defaultValue;
        }
        return value;
    }

    @Override
    public int insert(UserConfig userConfig) {
        int result = userConfigMapper.insert(userConfig);
        if (result > 0 && userConfig.getUserId() != null && userConfig.getConfigKey() != null) {
            eventPublisher.publishEvent(new UserConfigChangeEvent(userConfig.getUserId(),
                    Set.of(userConfig.getConfigKey())));
        }
        return result;
    }

    @Override
    public int update(UserConfig userConfig) {
        int result = userConfigMapper.update(userConfig);
        if (result > 0 && userConfig.getUserId() != null && userConfig.getConfigKey() != null) {
            eventPublisher.publishEvent(new UserConfigChangeEvent(userConfig.getUserId(),
                    Set.of(userConfig.getConfigKey())));
        }
        return result;
    }

    @Override
    public int deleteById(Long id) {
        return userConfigMapper.deleteById(id);
    }

    @Override
    public int deleteByUserIdAndKey(String userId, String key) {
        int result = userConfigMapper.deleteByUserIdAndKey(userId, key);
        if (result > 0 && userId != null && key != null) {
            eventPublisher.publishEvent(new UserConfigChangeEvent(userId, Set.of(key)));
        }
        return result;
    }
}
