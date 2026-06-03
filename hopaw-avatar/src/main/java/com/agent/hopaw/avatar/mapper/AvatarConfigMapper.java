package com.agent.hopaw.avatar.mapper;

import com.agent.hopaw.avatar.entity.AvatarConfig;
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

    int updateLastProcessedChatId(@Param("userId") String userId, @Param("lastProcessedChatId") Long lastProcessedChatId);

    int updateSoundEnabled(@Param("userId") String userId, @Param("soundEnabled") Boolean soundEnabled);
}
