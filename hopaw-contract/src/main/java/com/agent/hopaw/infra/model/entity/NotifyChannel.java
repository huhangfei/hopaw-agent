package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * 通知渠道实体：一个渠道代表一个具体的通知投递目标（如一个钉钉群机器人、一个飞书机器人、一组邮箱、一个 Webhook 地址）。
 * 同一通知方式（type）可配置多个渠道。
 * config 为 JSON 字符串，结构由各发送器按类型定义：
 * - dingtalk: {"webhookUrl":"https://oapi.dingtalk.com/robot/send?access_token=xxx","secret":"SECxxx"(可选)}
 * - feishu:   {"webhookUrl":"https://open.feishu.cn/open-apis/bot/v2/hook/xxx","secret":"xxx"(可选)}
 * - email:    {"receivers":"a@b.com,c@d.com"}
 * - webhook:  {"url":"http://xxx","headers":{"key":"value"}(可选)}
 */
public class NotifyChannel {
    private Long id;
    /** 渠道名称 */
    private String name;
    /** 渠道类型：dingtalk / feishu / email / webhook，见 NotifyChannelTypeEnum */
    private String type;
    /** 渠道配置（JSON），结构见类注释 */
    private String config;
    /** 是否启用 */
    private Boolean enabled;
    /** 所属用户 */
    private String userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getConfig() {
        return config;
    }

    public void setConfig(String config) {
        this.config = config;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
