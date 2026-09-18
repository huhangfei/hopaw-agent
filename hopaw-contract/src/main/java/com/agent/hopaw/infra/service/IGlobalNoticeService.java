package com.agent.hopaw.infra.service;

import com.agent.hopaw.infra.enums.GlobalNoticeTypeEnum;

import java.util.Map;

/**
 * 全局消息推送服务：向在线用户推送公共通知（项目消息、任务消息等）
 * 通过内部事件转发到 /ws/notice WebSocket 通道
 */
public interface IGlobalNoticeService {

    /**
     * 推送全局通知
     *
     * @param userId  接收用户（null 表示广播）
     * @param type    消息类型（project / task）
     * @param subtype 消息子类型（如 status_change）
     * @param content 消息内容（字典）
     */
    void notify(String userId, GlobalNoticeTypeEnum type, String subtype, Map<String, Object> content);
}
