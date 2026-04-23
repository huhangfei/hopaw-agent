package com.agent.hopaw.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface ChatMemoryMapper {
    List<Map<String, Object>> findByAgentId(@Param("agentId") Long agentId);
    
    int insert(@Param("agentId") Long agentId, @Param("messageId") String messageId, @Param("messageJson") String messageJson);
    
    int updateByMessageId(@Param("agentId") Long agentId, @Param("messageId") String messageId, @Param("messageJson") String messageJson);
    
    int deleteByMessageId(@Param("agentId") Long agentId, @Param("messageId") String messageId);
    
    int deleteByAgentId(@Param("agentId") Long agentId);
}
