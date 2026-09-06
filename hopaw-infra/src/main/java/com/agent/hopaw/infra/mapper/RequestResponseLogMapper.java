package com.agent.hopaw.infra.mapper;

import com.agent.hopaw.infra.model.entity.RequestResponseLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RequestResponseLogMapper {
    int insert(RequestResponseLog log);

    /**
     * 按会话查询请求日志，requestId 可选（按单次请求过滤）
     */
    List<RequestResponseLog> findBySessionId(@Param("sessionId") String sessionId,
                                             @Param("requestId") String requestId);

    RequestResponseLog findById(@Param("id") Long id);

    int deleteBySessionId(@Param("sessionId") String sessionId);
}
