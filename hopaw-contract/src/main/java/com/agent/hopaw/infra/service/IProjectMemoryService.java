package com.agent.hopaw.infra.service;

/**
 * 项目/任务维度记忆服务接口：与用户维度长时记忆（ILongTermMemoryService）并列。
 *
 * 差异定位：
 * - 用户记忆：按 (sessionId, userId) 总结，存 SQLite + 向量库，面向聊天会话；
 * - 项目/任务记忆：按项目/任务维度总结（跨用户共享——项目与任务本身不区分用户），
 *   以 Markdown 文件持久化到项目空间目录，随项目生命周期存在。
 *
 * 存储布局（项目空间内）：
 * - memory/project-memory.md        项目整体记忆（目标、进展、关键决策、经验教训）
 * - memory/task-{taskId}-memory.md  单任务执行记忆（每次执行/交互的增量总结）
 */
public interface IProjectMemoryService {

    /**
     * 读取项目整体记忆
     *
     * @param projectId 项目编号
     * @return 记忆内容，无记忆返回 null
     */
    String getProjectMemoryContent(Long projectId);

    /**
     * 读取指定任务记忆
     *
     * @param projectId 项目编号（决定记忆落盘位置）
     * @param taskId    任务编号
     * @return 记忆内容，无记忆返回 null
     */
    String getTaskMemoryContent(Long projectId, Long taskId);

    /**
     * 更新任务记忆：新会话纪要与现有记忆 AI 总结合并后写回任务记忆文件。
     *
     * @param projectId       项目编号（决定记忆落盘位置）
     * @param taskId          任务编号
     * @param newConversation 新增会话纪要文本
     * @param userId          触发本次整理的用户（用于定位项目空间与模型调用监听）
     */
    void updateTaskMemory(Long projectId, Long taskId, String newConversation, String userId);

    /**
     * 更新项目整体记忆：新会话纪要与现有记忆 AI 总结合并后写回。
     *
     * @param projectId       项目编号
     * @param newConversation 新增会话纪要文本
     * @param userId          触发本次整理的用户
     */
    void updateProjectMemory(Long projectId, String newConversation, String userId);
}