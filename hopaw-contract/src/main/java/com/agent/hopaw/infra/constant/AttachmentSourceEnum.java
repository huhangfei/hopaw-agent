package com.agent.hopaw.infra.constant;

/**
 * 附件来源统一枚举。
 * 各业务写入附件时统一引用本枚举的 source 值，避免散落的魔法字符串。
 * 前端展示文案见 {@code js/page/attachment-source.js}（ATTACHMENT_SOURCE_LABELS），两者需保持一致。
 */
public enum AttachmentSourceEnum {
    /** 附件中心手动/批量上传 */
    UPLOAD("upload", "附件上传"),
    /** 会话文件 */
    CHAT("chat", "会话文件"),
    /** 智能体工具添加 */
    AGENT_TOOL("agentTool", "智能体工具");

    private final String code;
    private final String description;

    AttachmentSourceEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据 code 解析来源描述；未知 code 返回 code 本身
     */
    public static String descriptionOf(String code) {
        if (code == null) {
            return "";
        }
        for (AttachmentSourceEnum s : values()) {
            if (s.code.equals(code)) {
                return s.description;
            }
        }
        return code;
    }
}