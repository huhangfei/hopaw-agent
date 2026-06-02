package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatHistoryMapper {
    List<ChatHistory> findByAgentId(@Param("agentId") Long agentId, @Param("limit") int limit);
    List<ChatHistory> findBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);
    ChatHistory findBySessionIdAndToolCallId(@Param("sessionId") String sessionId, @Param("toolCallId") String toolCallId);

    List<ChatHistory> findByAgentIdAfterId(@Param("agentId") Long agentId, @Param("afterId") Long afterId);

    List<ChatHistory> findAllAfterId(@Param("afterId") Long afterId);

    List<Long> findDistinctAgentIds();

    int insert(ChatHistory chat);

    int insertBatch(List<ChatHistory> list);

    int deleteByAgentId(@Param("agentId") Long agentId);
    int deleteBySessionId(@Param("sessionId") String sessionId);

    int updateToolCallStatusAndContent(@Param("id") Long id, @Param("toolCallStatus") String toolCallStatus, @Param("arguments") String arguments, @Param("content") String content, @Param("toolExecutionTime") Long toolExecutionTime);
}
