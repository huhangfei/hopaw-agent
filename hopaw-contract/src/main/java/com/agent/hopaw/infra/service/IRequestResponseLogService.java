package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.RequestResponseLog;

import java.util.List;

/**
 * 请求响应日志服务：存储与查询每次模型调用的完整请求/响应细节，用于问题排查
 */
public interface IRequestResponseLogService {
    /**
     * 新增一条请求响应日志（异步落库失败仅记录日志，不影响主流程）
     */
    void saveLog(RequestResponseLog log);

    /**
     * 按会话查询请求日志（倒序），requestId 可选
     */
    List<RequestResponseLog> findBySessionId(String sessionId, String requestId);

    RequestResponseLog findById(Long id);

    /**
     * 清理指定会话的全部请求日志
     */
    int deleteBySessionId(String sessionId);
}
