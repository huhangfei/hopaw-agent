package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * 任务附件关系实体
 */
public class TaskAttachment {
    private Long id;
    private Long taskId;
    private Long attachmentId;
    private LocalDateTime createTime;

    /** 附件原始文件名（非持久字段，JOIN 查询填充） */
    private String originalName;
    /** 文件类型（非持久字段，JOIN 查询填充） */
    private String fileType;
    /** 文件访问URL（非持久字段，JOIN 查询填充） */
    private String url;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(Long attachmentId) {
        this.attachmentId = attachmentId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
