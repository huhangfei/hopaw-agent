package com.agent.hopaw.infra.notify;

import com.agent.hopaw.infra.model.entity.NotifyChannel;

/**
 * 通知发送器公共接口：每种通知方式（钉钉群/邮件/飞书/Webhook等）实现一个发送器。
 * 由 NotificationService 按渠道类型路由到对应发送器。
 */
public interface NotifySender {

    /**
     * 发送器支持的通知方式类型，与 NotifyChannelTypeEnum.code 对应。
     */
    String getType();

    /**
     * 发送通知。
     *
     * @param channel 通知渠道（含该类型的配置）
     * @param title   通知标题
     * @param content 通知内容
     * @return 发送成功返回 null，失败返回错误原因（便于调用方记录与展示）
     */
    String send(NotifyChannel channel, String title, String content);
}
