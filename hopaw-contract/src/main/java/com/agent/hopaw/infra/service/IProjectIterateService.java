package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.dto.ProjectIterateResult;
import com.agent.hopaw.infra.model.dto.UserChatRequest;

/**
 * 项目自动迭代服务接口：
 * 由定时任务周期调用，驱动配置了项目管理智能体且启用自动迭代的进行中项目，
 * 创建项目管理智能体执行器完成项目分析、任务创建、审核验收与项目完结。
 */
public interface IProjectIterateService {

    /**
     * 对单个项目执行一轮自动迭代。
     * 内部复用项目会话保留上下文；执行器运行中时跳过本轮。
     *
     * @param projectId 项目编号
     * @return 执行结果（含是否成功与失败/跳过原因）
     */
    ProjectIterateResult executeProjectIterate(Long projectId);
    /**
     * 对单个项目执行一轮自动迭代。
     * 内部复用项目会话保留上下文；执行器运行中时跳过本轮。
     *
     * @param projectId 项目编号
     * @param userMessage 用户消息
     * @return 执行结果（含是否成功与失败/跳过原因）
     */
    ProjectIterateResult executeProjectIterate(Long projectId,String userMessage);

    /**
     * 手动下发指令执行一轮项目迭代（项目详情页入口）。
     * 不校验项目自动迭代开关，仅做基础校验与执行器并发检查（执行器运行中时拒绝提交）。
     *
     * @param projectId 项目编号
     * @param userMessage 用户指令，为空时回退默认迭代指令
     * @return 执行结果（含是否成功与失败/跳过原因）
     */
    ProjectIterateResult executeProjectIterateManual(Long projectId,String userMessage);

    /**
     * 用户从首页在项目会话中发起消息：重新唤起项目管理智能体会话，
     * 复用项目会话编号保留上下文，以用户消息驱动一轮项目处理。
     *
     * @param userChatRequest 用户聊天请求（sessionId 为项目会话编号）
     */
    void executeProjectChat(UserChatRequest userChatRequest);
}
