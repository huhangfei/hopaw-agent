package com.agent.hopaw.infra.listener;

import com.agent.hopaw.infra.constant.AgentExecutorBizTypeEnum;
import com.agent.hopaw.infra.event.AgentMessageEvent;
import com.agent.hopaw.infra.event.TokenUsageEvent;
import com.agent.hopaw.infra.executor.IAgentExecutor;
import com.agent.hopaw.infra.mapper.ChatHistoryMapper;
import com.agent.hopaw.infra.mapper.TokenUsageMapper;
import com.agent.hopaw.infra.model.dto.AiMessageBaseInfo;
import com.agent.hopaw.infra.model.dto.AiTaskStatsMessageInfo;
import com.agent.hopaw.infra.model.entity.TokenUsage;
import com.agent.hopaw.infra.service.IAgentExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * 任务执行统计事件监听：task-done 结束消息与 Token 用量事件触发，
 * 统计本次请求的工具执行次数（chat历史）、Token用量（用量历史）并计算每秒Token，
 * 发布 task-stats 统计消息供前端展示。
 */
@Service
public class TaskStatsEventListener {

    private static final Logger logger = LoggerFactory.getLogger(TaskStatsEventListener.class);

    private final ChatHistoryMapper chatHistoryMapper;
    private final TokenUsageMapper tokenUsageMapper;
    private final IAgentExecutorService agentExecutorService;
    private final ApplicationEventPublisher eventPublisher;

    public TaskStatsEventListener(ChatHistoryMapper chatHistoryMapper,
                                  TokenUsageMapper tokenUsageMapper,
                                  IAgentExecutorService agentExecutorService,
                                  ApplicationEventPublisher eventPublisher) {
        this.chatHistoryMapper = chatHistoryMapper;
        this.tokenUsageMapper = tokenUsageMapper;
        this.agentExecutorService = agentExecutorService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 监听执行器结束消息（task-done，携带运行总时长）：出本次请求的最终执行统计。
     * 统计异常仅记日志，不影响 task-done 主流程。
     */
    @EventListener
    public void listenAgentMessageEvent(AgentMessageEvent event) {
        AiMessageBaseInfo message = event.getMessage();
        if (message == null || !"task-done".equals(message.getType())) {
            return;
        }
        long elapsedMs = message.getElapsedMs() != null ? message.getElapsedMs() : 0L;
        publishTaskStats(event.getUserId(), event.getAgentId(), message.getSessionId(),
                message.getRequestId(), elapsedMs, message.getBizType());
    }

    /**
     * 监听 Token 用量事件（每次模型调用完成）：任务运行中实时刷新执行统计，
     * 运行时长通过执行器服务接口获取（执行器已结束时跳过，等待 task-done 出最终统计）。
     */
    @EventListener
    public void listenTokenUsageEvent(TokenUsageEvent event) {
        String requestId = event.getRequestId();
        if (requestId == null || event.getSessionId() == null) {
            return;
        }
        try {
            IAgentExecutor executor = agentExecutorService.getAgentExecutor(event.getSessionId());
            if (executor == null) {
                return;
            }
            long elapsedMs = executor.getElapsedSeconds() * 1000L;
            AgentExecutorBizTypeEnum bizType = AgentExecutorBizTypeEnum.getByAiModelCallSource(event.getSource());
            publishTaskStats(event.getUserId(), event.getAgentId(), event.getSessionId(),
                    requestId, elapsedMs, bizType);
        } catch (Exception e) {
            logger.error("Token用量事件触发执行统计失败: sessionId={}, requestId={}", event.getSessionId(), requestId, e);
        }
    }

    /**
     * 统计本次请求的工具执行次数（chat历史，限会话范围）、Token用量（用量历史，限会话范围），
     * 计算每秒Token后发布 task-stats 统计消息供前端展示。
     */
    private void publishTaskStats(String userId, Long agentId, String sessionId, String requestId,
                                  long elapsedMs, AgentExecutorBizTypeEnum bizType) {
        try {
            // 工具执行次数与耗时（chat历史，限会话范围）
            int toolCallCount = chatHistoryMapper.countToolCallsBySessionAndRequest(sessionId, requestId);
            Long toolElapsedSum = chatHistoryMapper.sumToolExecutionTimeBySessionAndRequest(sessionId, requestId);
            long toolElapsedMs = toolElapsedSum != null ? toolElapsedSum : 0L;
            // 工具耗时不可能超过运行总时长，异常数据时钳制
            toolElapsedMs = Math.min(Math.max(toolElapsedMs, 0L), elapsedMs);
            // Token用量（用量历史，限会话范围）
            TokenUsage usage = tokenUsageMapper.summaryBySessionAndRequest(sessionId, requestId);
            long inputTokens = usage != null && usage.getInputTokens() != null ? usage.getInputTokens() : 0L;
            long outputTokens = usage != null && usage.getOutputTokens() != null ? usage.getOutputTokens() : 0L;
            long totalTokens = usage != null && usage.getTotalTokens() != null ? usage.getTotalTokens() : 0L;
            // 每秒Token：输出Token用量 / 净生成时间（运行时长 - 工具耗时，衡量模型生成速度）
            long generationMs = elapsedMs - toolElapsedMs;
            double tokensPerSecond = generationMs > 0 ? outputTokens * 1000.0 / generationMs : 0.0;

            AiTaskStatsMessageInfo stats = AiTaskStatsMessageInfo.build(
                    sessionId, requestId, elapsedMs,
                    toolCallCount, toolElapsedMs, inputTokens, outputTokens, totalTokens, tokensPerSecond);
            stats.setBizType(bizType);
            eventPublisher.publishEvent(new AgentMessageEvent(userId, agentId, stats));
        } catch (Exception e) {
            logger.error("任务执行统计失败: sessionId={}, requestId={}", sessionId, requestId, e);
        }
    }
}
