package com.agent.hopaw.infra.model.dto;

/**
 * 附件文件
 */
public class AttachmentFile {
    /** 文件访问地址 */
    private String url;
    /** 文件类型：image、pdf 等 */
    private String type;

    public AttachmentFile() {}

    public AttachmentFile(String url, String type) {
        this.url = url;
        this.type = type;
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
}