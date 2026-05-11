package com.agent.hopaw.mapper;

import com.agent.hopaw.model.ChatMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ChatMemoryMapper {
    List<ChatMemory> findByAgentId(@Param("agentId") Long agentId);

    List<ChatMemory> findByAgentIdAndUserId(@Param("agentId") Long agentId, @Param("userId") String userId);

    List<ChatMemory> findByAgentIdAndUserIdInStatus(@Param("agentId") Long agentId, @Param("userId") String userId, @Param("status") List<Integer> status);

    List<ChatMemory> findByAgentIdAndUserIdAndStatus(@Param("agentId") Long agentId, @Param("userId") String userId, @Param("status") Integer status);

    int insert(@Param("agentId") Long agentId, @Param("userId") String userId, @Param("messageId") String messageId, @Param("messageJson") String messageJson, @Param("createTime") LocalDateTime createTime);

    int updateByMessageId(@Param("agentId") Long agentId, @Param("userId") String userId, @Param("messageId") String messageId, @Param("messageJson") String messageJson);

    int updateStatusByAgentId(@Param("agentId") Long agentId, @Param("status") Integer status);

    int deleteByMessageId(@Param("agentId") Long agentId, @Param("userId") String userId, @Param("messageId") String messageId);

    int deleteByAgentId(@Param("agentId") Long agentId);

    int deleteByAgentIdAndUserId(@Param("agentId") Long agentId, @Param("userId") String userId);

    int deleteByIds(@Param("ids") List<Long> ids);

    int updateStatus(@Param("agentId") Long agentId, @Param("userId") String userId, @Param("messageId") String messageId, @Param("status") Integer status);
}
