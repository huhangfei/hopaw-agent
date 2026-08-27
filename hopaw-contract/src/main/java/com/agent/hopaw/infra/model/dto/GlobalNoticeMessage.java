package com.agent.hopaw.infra.model.dto;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局通知消息：通过 /ws/notice 推送的公共消息载体
 * 消息内容为字典类型，具体字段由各消息子类型自行约定
 */
public class GlobalNoticeMessage {

    /** 消息类型 */
    private String type;
    /** 消息子类型（如 status_change） */
    private String subtype;
    /** 消息内容（字典） */
    private Map<String, Object> content = new HashMap<>();
    /** 消息产生时间（毫秒时间戳） */
    private long timestamp = System.currentTimeMillis();

    public GlobalNoticeMessage() {
    }

    public GlobalNoticeMessage(String type, String subtype) {
        this.type = type;
        this.subtype = subtype;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSubtype() {
        return subtype;
    }

    public void setSubtype(String subtype) {
        this.subtype = subtype;
    }

    public Map<String, Object> getContent() {
        return content;
    }

    public void setContent(Map<String, Object> content) {
        this.content = content;
    }

    /** 追加一个内容键值 */
    public GlobalNoticeMessage put(String key, Object value) {
        this.content.put(key, value);
        return this;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
