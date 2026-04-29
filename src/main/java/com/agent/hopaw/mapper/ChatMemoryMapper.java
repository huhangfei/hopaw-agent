package com.agent.hopaw.mapper;

import com.agent.hopaw.model.ChatMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatMemoryMapper {
    List<ChatMemory> findByAgentId(@Param("agentId") Long agentId);

    List<ChatMemory> findByAgentIdAndCleaned(@Param("agentId") Long agentId, @Param("cleaned") Integer cleaned);

    int insert(@Param("agentId") Long agentId, @Param("messageId") String messageId, @Param("messageJson") String messageJson);

    int updateByMessageId(@Param("agentId") Long agentId, @Param("messageId") String messageId, @Param("messageJson") String messageJson);

    int deleteByMessageId(@Param("agentId") Long agentId, @Param("messageId") String messageId);

    int deleteByAgentId(@Param("agentId") Long agentId);

    int deleteByIds(@Param("ids") List<Long> ids);

    int markCleaned(@Param("agentId") Long agentId, @Param("messageId") String messageId);

    int unmarkCleaned(@Param("agentId") Long agentId, @Param("messageId") String messageId);
}
