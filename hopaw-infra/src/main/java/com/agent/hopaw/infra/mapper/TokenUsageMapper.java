package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.TokenUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface TokenUsageMapper {
    int insert(TokenUsage tokenUsage);

    List<TokenUsage> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime,
                                     @Param("userId") String userId,
                                     @Param("agentId") Long agentId,
                                     @Param("modelName") String modelName,
                                     @Param("source") String source,
                                     @Param("sessionId") String sessionId,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    long countByTimeRange(@Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime,
                          @Param("userId") String userId,
                          @Param("agentId") Long agentId,
                          @Param("modelName") String modelName,
                          @Param("source") String source,
                          @Param("sessionId") String sessionId);

    TokenUsage summaryByTimeRange(@Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime,
                                  @Param("userId") String userId,
                                  @Param("agentId") Long agentId,
                                  @Param("modelName") String modelName,
                                  @Param("source") String source,
                                  @Param("sessionId") String sessionId);

    List<Map<String, Object>> dailyStatsByTimeRange(@Param("startTime") LocalDateTime startTime,
                                                     @Param("endTime") LocalDateTime endTime,
                                                     @Param("userId") String userId,
                                                     @Param("agentId") Long agentId,
                                                     @Param("modelName") String modelName,
                                                     @Param("source") String source,
                                                     @Param("sessionId") String sessionId);

    List<TokenUsage> findTodayByAgentUser(@Param("agentId") Long agentId,
                                          @Param("userId") String userId,
                                          @Param("source") String source,
                                          @Param("sessionId") String sessionId,
                                          @Param("minId") Long minId,
                                          @Param("limit") int limit);

}