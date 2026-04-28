package com.agent.hopaw.mapper;

import com.agent.hopaw.model.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatHistoryMapper {
    List<ChatHistory> findByAgentId(@Param("agentId") Long agentId, @Param("limit") int limit);
    ChatHistory findByAgentIdAndToolCallId(@Param("agentId") Long agentId, @Param("toolCallId") String toolCallId);

    List<ChatHistory> findByAgentIdAfterId(@Param("agentId") Long agentId, @Param("afterId") Long afterId);

    List<ChatHistory> findAllAfterId(@Param("afterId") Long afterId);

    List<Long> findDistinctAgentIds();

    int insert(ChatHistory chat);

    int deleteByAgentId(@Param("agentId") Long agentId);

    int updateToolCallStatusAndContent(@Param("id") Long id, @Param("toolCallStatus") String toolCallStatus, @Param("content") String content);
}
