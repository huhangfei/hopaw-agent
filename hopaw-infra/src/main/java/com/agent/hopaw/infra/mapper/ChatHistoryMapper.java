package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ChatHistoryMapper {
    List<ChatHistory> findByAgentId(@Param("agentId") Long agentId, @Param("limit") int limit);
    List<ChatHistory> findBySessionId(@Param("sessionId") String sessionId, @Param("limit") int limit);
    ChatHistory findBySessionIdAndToolCallId(@Param("sessionId") String sessionId, @Param("toolCallId") String toolCallId);

    /** 会话最后一条记录的时间（无记录返回null），作为会话最后活动时间 */
    LocalDateTime findLastCreateTimeBySessionId(@Param("sessionId") String sessionId);

    /** 统计会话的工具调用总数（message_type='tool_call'） */
    int countToolCallsBySessionId(@Param("sessionId") String sessionId);

    /** 批量统计多个会话的消息记录数量（返回 session_id / cnt） */
    List<Map<String, Object>> countMessagesBySessionIds(@Param("sessionIds") List<String> sessionIds);

    List<ChatHistory> findByAgentIdAfterId(@Param("agentId") Long agentId, @Param("afterId") Long afterId);

    List<ChatHistory> findAllAfterId(@Param("afterId") Long afterId);

    List<Long> findDistinctAgentIds();

    List<ChatHistory> findRecentByUserIdAndAagentIdAndAfterId(@Param("userId") String userId,
                                                       @Param("agentId") Long agentId,
                                                       @Param("afterId") Long afterId,
                                                              @Param("limit") int limit);

    int insert(ChatHistory chat);

    int insertBatch(List<ChatHistory> list);

    int deleteByAgentId(@Param("agentId") Long agentId);
    int deleteBySessionId(@Param("sessionId") String sessionId);

    int updateToolCallStatusAndContent(@Param("id") Long id, @Param("toolCallStatus") String toolCallStatus, @Param("arguments") String arguments, @Param("content") String content, @Param("toolExecutionTime") Long toolExecutionTime);
}
