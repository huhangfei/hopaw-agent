package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.BizTokenUsage;

import java.util.Map;

/**
 * 项目/工作流任务维度 Token 用量服务
 */
public interface IBizTokenUsageService {

    /**
     * 项目维度用量统计：汇总 + 最近记录（柱状图用）
     */
    Map<String, Object> getProjectUsage(Long projectId, int limit);

    /**
     * 任务维度用量统计：汇总 + 最近记录（柱状图用）
     */
    Map<String, Object> getTaskUsage(Long taskId, int limit);
}
