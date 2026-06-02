package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.AvatarConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AvatarConfigMapper {
    AvatarConfig findByUserId(@Param("userId") String userId);

    List<AvatarConfig> findAll();

    int insert(AvatarConfig avatarConfig);

    int update(AvatarConfig avatarConfig);

    int upsert(AvatarConfig avatarConfig);

    int addTotalTokens(@Param("userId") String userId, @Param("tokens") long tokens);
}
