package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * 提示词实体
 */
public class Prompt {
    private Long id;
    /** 名称 */
    private String name;
    /** 内容 */
    private String content;
    /** 标签（多个用逗号分隔） */
    private String tags;
    /** 热度值（复制次数） */
    private Integer heat;
    /** 用户ID */
    private String userId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public Integer getHeat() { return heat; }
    public void setHeat(Integer heat) { this.heat = heat; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
