package com.agent.hopaw.mapper;

import com.agent.hopaw.model.TokenUsage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TokenUsageMapper {
    int insert(TokenUsage tokenUsage);

    List<TokenUsage> findByTimeRange(@Param("startTime") LocalDateTime startTime,
                                     @Param("endTime") LocalDateTime endTime,
                                     @Param("userId") String userId,
                                     @Param("agentId") Long agentId,
                                     @Param("source") String source,
                                     @Param("limit") int limit,
                                     @Param("offset") int offset);

    long countByTimeRange(@Param("startTime") LocalDateTime startTime,
                          @Param("endTime") LocalDateTime endTime,
                          @Param("userId") String userId,
                          @Param("agentId") Long agentId,
                          @Param("source") String source);

    TokenUsage summaryByTimeRange(@Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime,
                                  @Param("userId") String userId,
                                  @Param("agentId") Long agentId,
                                  @Param("source") String source);
}
