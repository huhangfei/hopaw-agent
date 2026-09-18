package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.UserConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserConfigMapper {
    List<UserConfig> findByUserId(@Param("userId") String userId);

    UserConfig findByUserIdAndKey(@Param("userId") String userId, @Param("key") String key);

    List<UserConfig> findByUserIdAndKeys(@Param("userId") String userId, @Param("keys") List<String> keys);

    int insert(UserConfig userConfig);

    int update(UserConfig userConfig);

    int deleteById(@Param("id") Long id);

    int deleteByUserIdAndKey(@Param("userId") String userId, @Param("key") String key);
}
