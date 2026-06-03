package com.agent.hopaw.avatar.mapper;

import com.agent.hopaw.avatar.entity.AgentAvatarConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AvatarConfigMapper {
    AgentAvatarConfig findByUserAndAgent(@Param("userId") String userId,
                                          @Param("agentId") Long agentId);

    List<AgentAvatarConfig> findByUserId(@Param("userId") String userId);

    List<AgentAvatarConfig> findAll();

    int insert(AgentAvatarConfig config);

    int update(AgentAvatarConfig config);

    int upsert(AgentAvatarConfig config);

    int addTotalTokens(@Param("userId") String userId,
                       @Param("agentId") Long agentId,
                       @Param("tokens") long tokens);

    int updateLastProcessedChatId(@Param("userId") String userId,
                                  @Param("agentId") Long agentId,
                                  @Param("lastProcessedChatId") Long lastProcessedChatId);

    int updateSoundEnabled(@Param("userId") String userId,
                           @Param("agentId") Long agentId,
                           @Param("soundEnabled") Boolean soundEnabled);

    int updateLastProactiveGreetingTime(@Param("userId") String userId,
                                        @Param("agentId") Long agentId,
                                        @Param("lastProactiveGreetingTime") String lastProactiveGreetingTime);

    int deleteByUserAndAgent(@Param("userId") String userId,
                             @Param("agentId") Long agentId);
}
