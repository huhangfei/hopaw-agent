package com.agent.hopaw.infra.service;

/**
 * 公共通知服务：按渠道编号发送通知，或按项目配置（渠道多选 + 事项多选）在事件发生时发送通知。
 * 发送为异步执行，失败不影响业务主流程（仅记录日志）。
 */
public interface INotificationService {

    /**
     * 按渠道编号发送通知。
     *
     * @param channelId 渠道编号
     * @param title     通知标题
     * @param content   通知内容
     * @return null=发送成功（或已受理），否则为错误原因
     */
    String sendByChannelId(Long channelId, String title, String content);

    /**
     * 按项目通知配置发送：校验项目已启用该通知事项后，向项目绑定的全部渠道发送。
     * 项目不存在或未配置该事项时静默跳过；发送异常仅记录日志。
     *
     * @param projectId 项目编号
     * @param eventCode 通知事项编码，见 NotifyEventEnum
     * @param title     通知标题
     * @param content   通知内容（将自动附加项目名称前缀）
     */
    void sendForProject(Long projectId, String eventCode, String title, String content);
}
