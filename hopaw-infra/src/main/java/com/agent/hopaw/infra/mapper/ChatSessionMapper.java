package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ChatSessionMapper {
    List<ChatSession> findAll();

    List<ChatSession> findByUserId(@Param("userId") String userId);

    List<ChatSession> findByUserIdAndAgentId(@Param("userId") String userId, @Param("agentId") Long agentId);

    ChatSession findById(@Param("id") Long id);

    ChatSession findBySessionId(@Param("sessionId") String sessionId);

    int insert(ChatSession chatSession);

    int update(ChatSession chatSession);

    int updateTitle(@Param("id") Long id, @Param("title") String title);

    int deleteById(@Param("id") Long id);

    int deleteBySessionId(@Param("sessionId") String sessionId);

    int deleteByAgentId(@Param("agentId") Long agentId);
}
