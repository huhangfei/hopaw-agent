package com.agent.hopaw.infra.model.entity;

import java.time.LocalDateTime;

/**
 * 附件实体
 */
public class Attachment {
    private Long id;
    /** 原始文件名 */
    private String originalName;
    /** 存储文件名（UUID + 扩展名） */
    private String storageName;
    /** 文件访问URL */
    private String url;
    /** 文件类型：image, video, audio, pdf, text, markdown, file */
    private String fileType;
    /** 文件扩展名，如 .png .txt */
    private String fileExtension;
    /** 文件大小（字节） */
    private Long fileSize;
    /** MIME类型 */
    private String mimeType;
    /** 附件来源：upload(附件上传), task(任务附件), project(项目附件) 等 */
    private String source;
    /** 关联业务ID（可选，如任务ID、项目ID） */
    private Long bizId;
    /** 标签（多个用逗号分隔） */
    private String tags;
    /** 备注 */
    private String remark;
    /** 用户ID */
    private String userId;
    /** 存储路径（相对路径，如 2026-08-12/xxx.png） */
    private String storagePath;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getStorageName() {
        return storageName;
    }

    public void setStorageName(String storageName) {
        this.storageName = storageName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Long getBizId() {
        return bizId;
    }

    public void setBizId(Long bizId) {
        this.bizId = bizId;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
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
