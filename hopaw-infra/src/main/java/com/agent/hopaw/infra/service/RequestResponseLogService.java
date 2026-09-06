package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.mapper.RequestResponseLogMapper;
import com.agent.hopaw.infra.model.entity.RequestResponseLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RequestResponseLogService implements IRequestResponseLogService {
    private static final Logger logger = LoggerFactory.getLogger(RequestResponseLogService.class);

    private final RequestResponseLogMapper requestResponseLogMapper;
    /** 落库专用单线程池：串行写入，不阻塞模型调用主流程 */
    private final ExecutorService logExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "request-response-log-writer");
        t.setDaemon(true);
        return t;
    });

    public RequestResponseLogService(RequestResponseLogMapper requestResponseLogMapper) {
        this.requestResponseLogMapper = requestResponseLogMapper;
    }

    @Override
    public void saveLog(RequestResponseLog log) {
        if (log == null) {
            return;
        }
        logExecutor.execute(() -> {
            try {
                requestResponseLogMapper.insert(log);
            } catch (Exception e) {
                logger.error("保存请求响应日志失败: sessionId={}, requestId={}",
                        log.getSessionId(), log.getRequestId(), e);
            }
        });
    }

    @Override
    public List<RequestResponseLog> findBySessionId(String sessionId, String requestId) {
        return requestResponseLogMapper.findBySessionId(sessionId, requestId);
    }

    @Override
    public RequestResponseLog findById(Long id) {
        return requestResponseLogMapper.findById(id);
    }

    @Override
    public int deleteBySessionId(String sessionId) {
        return requestResponseLogMapper.deleteBySessionId(sessionId);
    }
}
