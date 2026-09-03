package com.agent.hopaw.infra.model.dto;

/**
 * 附件文件
 */
public class AttachmentFile {
    /** 文件访问地址 */
    private String url;
    /** 文件类型：image、pdf 等 */
    private String type;
    private String originalName;
    private Long id;

    public AttachmentFile() {}

    public AttachmentFile(String url, String type, String originalName, Long id) {
        this.url = url;
        this.type = type;
        this.originalName = originalName;
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
    public String getOriginalName() {
        return originalName;
    }
    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }
    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
}