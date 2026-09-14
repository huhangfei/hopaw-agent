package com.agent.hopaw.infra.model.dto;

/**
 * 任务执行统计消息：task-done 后由消费方统计并发送，
 * 汇总本次请求的运行时长、工具执行次数、Token用量与每秒Token
 */
public class AiTaskStatsMessageInfo extends AiMessageBaseInfo {
    public static final String TYPE_TASK_STATS = "task-stats";

    /** 工具执行次数（chat历史统计） */
    private Integer toolCallCount;
    /** 工具执行耗时（毫秒，chat历史统计）：Token/秒计算时从运行时长中扣除 */
    private Long toolElapsedMs;
    /** 输入Token用量（用量历史汇总） */
    private Long inputTokens;
    /** 输出Token用量（用量历史汇总） */
    private Long outputTokens;
    /** 总Token用量（用量历史汇总） */
    private Long totalTokens;
    /** 每秒Token（输出Token用量 / 运行时长，衡量生成速度） */
    private Double tokensPerSecond;

    public AiTaskStatsMessageInfo() {
        super(TYPE_TASK_STATS);
    }

    public static AiTaskStatsMessageInfo build(String sessionId, String requestId, Long elapsedMs, Integer toolCallCount,
                                               Long toolElapsedMs, Long inputTokens, Long outputTokens,
                                               Long totalTokens, Double tokensPerSecond) {
        AiTaskStatsMessageInfo info = new AiTaskStatsMessageInfo();
        info.setSessionId(sessionId);
        info.setRequestId(requestId);
        info.setElapsedMs(elapsedMs);
        info.setToolCallCount(toolCallCount);
        info.setToolElapsedMs(toolElapsedMs);
        info.setInputTokens(inputTokens);
        info.setOutputTokens(outputTokens);
        info.setTotalTokens(totalTokens);
        info.setTokensPerSecond(tokensPerSecond);
        return info;
    }

    public Integer getToolCallCount() { return toolCallCount; }
    public void setToolCallCount(Integer toolCallCount) { this.toolCallCount = toolCallCount; }
    public Long getToolElapsedMs() { return toolElapsedMs; }
    public void setToolElapsedMs(Long toolElapsedMs) { this.toolElapsedMs = toolElapsedMs; }
    public Long getInputTokens() { return inputTokens; }
    public void setInputTokens(Long inputTokens) { this.inputTokens = inputTokens; }
    public Long getOutputTokens() { return outputTokens; }
    public void setOutputTokens(Long outputTokens) { this.outputTokens = outputTokens; }
    public Long getTotalTokens() { return totalTokens; }
    public void setTotalTokens(Long totalTokens) { this.totalTokens = totalTokens; }
    public Double getTokensPerSecond() { return tokensPerSecond; }
    public void setTokensPerSecond(Double tokensPerSecond) { this.tokensPerSecond = tokensPerSecond; }
}
