package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.model.entity.NotifyChannel;

import java.util.List;

/**
 * 通知渠道管理服务：渠道的增删改查（渠道持久化到 notify_channels 表）。
 */
public interface INotifyChannelService {

    /** 创建渠道，返回带主键的实体 */
    NotifyChannel createChannel(NotifyChannel channel);

    /** 更新渠道（校验归属），返回更新后的实体 */
    NotifyChannel updateChannel(NotifyChannel channel, String userId);

    /** 删除渠道（校验归属） */
    boolean deleteChannel(Long id, String userId);

    /** 按编号查询（校验归属），不存在或无权返回 null */
    NotifyChannel getChannel(Long id, String userId);

    /** 查询用户全部渠道 */
    List<NotifyChannel> listByUser(String userId);
}
